import re
from pathlib import Path, PurePosixPath
from typing import Any

from app.models.lesson_document import (
    FormulaBlock,
    HeadingBlock,
    ImageBlock,
    LessonBlock,
    LessonDocument,
    LessonMetadata,
    ListBlock,
    ParagraphBlock,
    TableBlock,
)


class MinerUAdapter:
    def convert(
        self,
        mineru_result: dict[str, Any],
        *,
        document_id: str,
        source_file_name: str,
        asset_urls: dict[str, str] | None = None,
    ) -> LessonDocument:
        asset_urls = asset_urls or {}
        blocks: list[LessonBlock] = []
        title = Path(source_file_name).stem

        for index, item in enumerate(mineru_result.get("content_list", []), start=1):
            block = self._convert_item(item, index, asset_urls)
            if block is None:
                continue
            blocks.append(block)
            if isinstance(block, HeadingBlock) and block.level == 1 and title == Path(source_file_name).stem:
                title = block.text

        return LessonDocument(
            document_id=document_id,
            title=title,
            metadata=LessonMetadata(
                source_type=Path(source_file_name).suffix.lstrip(".").lower(),
                source_file_name=source_file_name,
            ),
            blocks=blocks,
        )

    def _convert_item(
        self, item: dict[str, Any], index: int, asset_urls: dict[str, str]
    ) -> LessonBlock | None:
        item_type = str(item.get("type", "")).lower()
        block_id = f"block-{index:04d}"
        text = self._text(item)

        if item_type in {"title", "heading"} or item.get("text_level"):
            if not text:
                return None
            level = int(item.get("text_level") or item.get("level") or 1)
            return HeadingBlock(id=block_id, type="heading", level=min(max(level, 1), 6), text=text)

        if item_type in {"list", "list_item"}:
            items = item.get("items") or item.get("list_items") or []
            if not items and text:
                items = [line for line in text.splitlines() if line.strip()]
            normalized = [self._text(value) if isinstance(value, dict) else str(value).strip() for value in items]
            normalized = [value for value in normalized if value]
            return ListBlock(
                id=block_id,
                type="list",
                items=normalized,
                ordered=bool(item.get("ordered") or item.get("list_type") == "ordered"),
            ) if normalized else None

        if item_type == "table":
            html = str(item.get("table_body") or item.get("html") or "").strip()
            html = self._rewrite_table_assets(html, asset_urls)
            return TableBlock(id=block_id, type="table", html=html) if html else None

        if item_type == "image":
            raw_path = str(item.get("img_path") or item.get("image_path") or item.get("src") or "")
            src = self._asset_url(raw_path, asset_urls)
            captions = item.get("image_caption") or item.get("caption") or []
            alt = " ".join(captions) if isinstance(captions, list) else str(captions)
            return ImageBlock(id=block_id, type="image", src=src, alt=alt or None) if src else None

        if item_type in {"equation", "formula", "interline_equation", "inline_equation"}:
            latex = str(item.get("latex") or text).strip().strip("$")
            return FormulaBlock(id=block_id, type="formula", latex=latex) if latex else None

        if item_type in {"text", "paragraph", ""} and text:
            list_lines = [line.strip() for line in text.splitlines() if line.strip()]
            if len(list_lines) > 1 and all(re.match(r"^(?:[-*•]|\d+[.)])\s+", line) for line in list_lines):
                ordered = all(re.match(r"^\d+[.)]\s+", line) for line in list_lines)
                items = [re.sub(r"^(?:[-*•]|\d+[.)])\s+", "", line) for line in list_lines]
                return ListBlock(id=block_id, type="list", items=items, ordered=ordered)
            return ParagraphBlock(id=block_id, type="paragraph", text=text)
        return None

    @staticmethod
    def _text(value: Any) -> str:
        if isinstance(value, dict):
            value = value.get("text") or value.get("content") or ""
        return str(value or "").strip()

    @staticmethod
    def _asset_url(raw_path: str, asset_urls: dict[str, str]) -> str:
        if not raw_path:
            return ""
        normalized = str(PurePosixPath(raw_path.replace("\\", "/")))
        return asset_urls.get(normalized) or asset_urls.get(PurePosixPath(normalized).name, "")

    @classmethod
    def _rewrite_table_assets(cls, html: str, asset_urls: dict[str, str]) -> str:
        def replace_src(match: re.Match[str]) -> str:
            source = match.group(2)
            resolved = cls._asset_url(source, asset_urls)
            return f"{match.group(1)}{resolved or source}{match.group(3)}"

        return re.sub(
            r"(<img\b[^>]*?\bsrc\s*=\s*[\"'])([^\"']+)([\"'])",
            replace_src,
            html,
            flags=re.IGNORECASE,
        )
