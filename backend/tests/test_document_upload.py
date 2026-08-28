from io import BytesIO
from pathlib import Path
from typing import Any

import pytest
from fastapi import UploadFile
from fastapi.testclient import TestClient

from app.adapters.mineru_adapter import MinerUAdapter
from app.core.config import Settings
from app.core.exceptions import AppError
from app.dependencies import get_document_service
from app.main import app
from app.parsers.base import DocumentParser
from app.services.asset_service import AssetService
from app.services.document_service import DocumentService
from app.storage.local_storage import LocalStorage


class _UnusedParser(DocumentParser):
    async def parse(self, file_path: Path) -> dict[str, Any]:
        raise AssertionError("create_job must not call MinerU")


def _service(tmp_path: Path) -> DocumentService:
    storage = LocalStorage(tmp_path, max_file_size_mb=200)
    return DocumentService(
        settings=Settings(),
        parser=_UnusedParser(),
        adapter=MinerUAdapter(),
        storage=storage,
        asset_service=AssetService(storage),
    )


def _upload(filename: str | None, content: bytes = b"fake-bytes") -> UploadFile:
    return UploadFile(filename=filename, file=BytesIO(content))


@pytest.mark.parametrize("filename", ["lesson.doc", "lesson.docx", "lesson.pdf"])
async def test_create_job_accepts_word_and_pdf(tmp_path: Path, filename: str) -> None:
    job, source_path = await _service(tmp_path).create_job(_upload(filename))

    assert job.source_file_name == filename
    assert source_path.suffix == Path(filename).suffix
    assert source_path.is_file()


@pytest.mark.parametrize("filename", [None, "", "notes", "notes.txt", "archive.zip"])
async def test_create_job_rejects_unsupported_types(tmp_path: Path, filename: str | None) -> None:
    with pytest.raises(AppError) as exc_info:
        await _service(tmp_path).create_job(_upload(filename))

    assert exc_info.value.code == "UNSUPPORTED_FILE"
    assert exc_info.value.message == "不支持该文件类型"
    assert exc_info.value.status_code == 415
    assert not any(tmp_path.iterdir())


async def test_source_docx_is_downloaded_as_attachment(tmp_path: Path) -> None:
    service = _service(tmp_path)
    job, _ = await service.create_job(_upload("lesson.docx"))
    app.dependency_overrides[get_document_service] = lambda: service
    try:
        response = TestClient(app).get(f"/api/documents/{job.job_id}/source")
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    assert response.headers["content-type"].startswith(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    )
    assert "attachment" in response.headers["content-disposition"]


async def test_source_pdf_is_opened_inline(tmp_path: Path) -> None:
    service = _service(tmp_path)
    job, _ = await service.create_job(_upload("lesson.pdf"))
    app.dependency_overrides[get_document_service] = lambda: service
    try:
        response = TestClient(app).get(f"/api/documents/{job.job_id}/source")
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("application/pdf")
    assert "inline" in response.headers["content-disposition"]
