import asyncio
import json
import zipfile
from pathlib import Path
from typing import Any

import httpx

from app.core.config import Settings
from app.core.exceptions import MinerUError
from app.parsers.base import DocumentParser


class MinerUDocumentParser(DocumentParser):
    def __init__(self, settings: Settings) -> None:
        self.settings = settings

    @property
    def headers(self) -> dict[str, str]:
        if not self.settings.mineru_api_key:
            raise MinerUError("未配置 MINERU_API_KEY")
        return {"Authorization": f"Bearer {self.settings.mineru_api_key}"}

    async def parse(self, file_path: Path) -> dict[str, Any]:
        timeout = httpx.Timeout(self.settings.mineru_timeout_seconds)
        async with httpx.AsyncClient(timeout=timeout, follow_redirects=True) as client:
            batch_id, upload_url = await self._create_upload(client, file_path.name)
            await self._upload_file(client, upload_url, file_path)
            zip_url = await self._wait_for_result(client, batch_id, file_path.name)
            result_dir = file_path.parent / "mineru"
            await self._download_and_extract(client, zip_url, result_dir)

        content_file = self._find_content_list(result_dir)
        try:
            content_list = json.loads(content_file.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            raise MinerUError("MinerU content_list.json 读取失败") from exc

        if not isinstance(content_list, list):
            raise MinerUError("MinerU content_list.json 格式无效")
        return {
            "content_list": content_list,
            "ocr_layout": self._load_ocr_layout(result_dir),
            "page_tables": self._load_page_tables(result_dir),
            "result_dir": str(result_dir),
        }

    async def _create_upload(
        self, client: httpx.AsyncClient, file_name: str
    ) -> tuple[str, str]:
        payload = {
            "files": [{"name": file_name, "data_id": file_name}],
            "model_version": self.settings.mineru_model_version,
            "language": self.settings.mineru_language,
            "enable_table": True,
            "enable_formula": True,
        }
        response = await client.post(
            f"{self.settings.mineru_base_url}/file-urls/batch",
            headers=self.headers,
            json=payload,
        )
        data = self._json_response(response, "申请上传地址失败")
        result = data.get("data") or {}
        urls = result.get("file_urls") or []
        if not result.get("batch_id") or not urls:
            raise MinerUError("MinerU 未返回 batch_id 或上传地址")
        return str(result["batch_id"]), str(urls[0])

    async def _upload_file(
        self, client: httpx.AsyncClient, upload_url: str, file_path: Path
    ) -> None:
        response = await client.put(upload_url, content=file_path.read_bytes())
        if response.status_code not in {200, 201, 204}:
            raise MinerUError(f"文件上传失败（HTTP {response.status_code}）")

    async def _wait_for_result(
        self, client: httpx.AsyncClient, batch_id: str, file_name: str
    ) -> str:
        loop = asyncio.get_running_loop()
        deadline = loop.time() + self.settings.mineru_timeout_seconds
        while loop.time() < deadline:
            response = await client.get(
                f"{self.settings.mineru_base_url}/extract-results/batch/{batch_id}",
                headers=self.headers,
            )
            payload = self._json_response(response, "查询解析状态失败")
            raw_results = (payload.get("data") or {}).get("extract_result") or []
            results = raw_results if isinstance(raw_results, list) else [raw_results]
            result = next(
                (item for item in results if item.get("file_name") == file_name),
                results[0] if results else {},
            )
            state = result.get("state")
            if state == "done":
                if not result.get("full_zip_url"):
                    raise MinerUError("解析完成但未返回结果压缩包")
                return str(result["full_zip_url"])
            if state == "failed":
                raise MinerUError(str(result.get("err_msg") or "MinerU 解析失败"))
            await asyncio.sleep(self.settings.mineru_poll_interval_seconds)
        raise MinerUError("MinerU 解析超时")

    async def _download_and_extract(
        self, client: httpx.AsyncClient, zip_url: str, result_dir: Path
    ) -> None:
        response = await client.get(zip_url)
        if response.status_code != 200:
            raise MinerUError(f"结果下载失败（HTTP {response.status_code}）")
        archive_path = result_dir.parent / "mineru-result.zip"
        result_dir.mkdir(parents=True, exist_ok=True)
        archive_path.write_bytes(response.content)
        try:
            with zipfile.ZipFile(archive_path) as archive:
                root = result_dir.resolve()
                for member in archive.infolist():
                    target = (result_dir / member.filename).resolve()
                    if not target.is_relative_to(root):
                        raise MinerUError("MinerU 压缩包包含非法路径")
                archive.extractall(result_dir)
        except zipfile.BadZipFile as exc:
            raise MinerUError("MinerU 结果压缩包无效") from exc

    @staticmethod
    def _find_content_list(result_dir: Path) -> Path:
        matches = sorted(result_dir.rglob("*_content_list.json"))
        if not matches:
            matches = sorted(result_dir.rglob("content_list.json"))
        if not matches:
            raise MinerUError("MinerU 结果中缺少 content_list.json")
        return matches[0]

    @staticmethod
    def _load_ocr_layout(result_dir: Path) -> list[list[dict[str, Any]]]:
        """Keep only lightweight OCR boxes needed for inline spacing recovery."""

        matches = sorted(result_dir.rglob("*_model.json"))
        if not matches:
            return []
        try:
            model = json.loads(matches[0].read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return []
        if not isinstance(model, list):
            return []

        pages: list[list[dict[str, Any]]] = []
        for page in model:
            if not isinstance(page, list):
                pages.append([])
                continue
            boxes = []
            for item in page:
                if not isinstance(item, dict) or item.get("type") != "ocr_text":
                    continue
                bbox = item.get("bbox")
                if isinstance(bbox, list) and len(bbox) == 4:
                    boxes.append({"bbox": bbox})
            pages.append(boxes)
        return pages

    @staticmethod
    def _load_page_tables(result_dir: Path) -> list[list[dict[str, Any]]]:
        """Load page-local table HTML before MinerU merges cross-page tables.

        ``content_list.json`` intentionally merges table continuations and leaves
        later page entries empty. The model file retains the HTML recognized on
        each source page, which is the correct level for preserving page groups.
        """

        matches = sorted(result_dir.rglob("*_model.json"))
        if not matches:
            return []
        try:
            model = json.loads(matches[0].read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return []
        if not isinstance(model, list):
            return []

        pages: list[list[dict[str, Any]]] = []
        for page_index, page in enumerate(model):
            tables: list[dict[str, Any]] = []
            if isinstance(page, list):
                for item in page:
                    if not isinstance(item, dict) or item.get("type") != "table":
                        continue
                    content = item.get("content")
                    bbox = item.get("bbox")
                    if isinstance(content, str) and content.strip():
                        tables.append(
                            {
                                "type": "table",
                                "page_idx": page_index,
                                "bbox": bbox,
                                "table_body": content,
                            }
                        )
            pages.append(tables)
        return pages

    @staticmethod
    def _json_response(response: httpx.Response, context: str) -> dict[str, Any]:
        try:
            payload = response.json()
        except ValueError as exc:
            raise MinerUError(f"{context}：响应不是 JSON") from exc
        if response.status_code >= 400 or payload.get("code") != 0:
            message = payload.get("msg") or f"HTTP {response.status_code}"
            raise MinerUError(f"{context}：{message}")
        return payload
