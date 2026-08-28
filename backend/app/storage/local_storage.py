import json
import re
from pathlib import Path

from fastapi import UploadFile

from app.core.exceptions import AppError
from app.models.lesson_document import LessonDocument


class LocalStorage:
    def __init__(self, root: Path, max_file_size_mb: int) -> None:
        self.root = root.resolve()
        self.max_bytes = max_file_size_mb * 1024 * 1024
        self.root.mkdir(parents=True, exist_ok=True)

    def job_dir(self, job_id: str) -> Path:
        if not re.fullmatch(r"[a-f0-9-]{36}", job_id):
            raise AppError("ASSET_NOT_FOUND", "资源不存在", 404)
        path = (self.root / job_id).resolve()
        if not path.is_relative_to(self.root):
            raise AppError("ASSET_NOT_FOUND", "资源不存在", 404)
        return path

    async def save_upload(self, job_id: str, upload: UploadFile) -> Path:
        source_dir = self.job_dir(job_id)
        source_dir.mkdir(parents=True, exist_ok=False)
        suffix = Path(upload.filename or "source").suffix.lower()
        target = source_dir / f"source{suffix}"
        written = 0
        try:
            with target.open("wb") as output:
                while chunk := await upload.read(1024 * 1024):
                    written += len(chunk)
                    if written > self.max_bytes:
                        raise AppError("FILE_TOO_LARGE", "文件大小超过限制", 413)
                    output.write(chunk)
        except Exception:
            target.unlink(missing_ok=True)
            raise
        finally:
            await upload.close()
        return target

    def save_document(self, job_id: str, document: LessonDocument) -> None:
        path = self.job_dir(job_id) / "lesson-document.json"
        path.write_text(document.model_dump_json(by_alias=True, indent=2), encoding="utf-8")

    def save_provider_result(self, job_id: str, result: dict) -> None:
        path = self.job_dir(job_id) / "mineru-result.json"
        serializable = {key: value for key, value in result.items() if key != "result_dir"}
        path.write_text(json.dumps(serializable, ensure_ascii=False, indent=2), encoding="utf-8")

    def resolve_asset(self, job_id: str, filename: str) -> Path:
        assets_dir = (self.job_dir(job_id) / "assets").resolve()
        path = (assets_dir / filename).resolve()
        if not path.is_relative_to(assets_dir) or not path.is_file():
            raise AppError("ASSET_NOT_FOUND", "资源不存在", 404)
        return path

    def resolve_source(self, job_id: str) -> Path:
        job_dir = self.job_dir(job_id)
        matches = sorted(job_dir.glob("source.*"))
        if len(matches) != 1 or not matches[0].is_file():
            raise AppError("SOURCE_NOT_FOUND", "源文件不存在", 404)
        return matches[0]
