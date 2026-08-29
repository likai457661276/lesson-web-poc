from io import BytesIO
from pathlib import Path
from typing import Any

import pytest
from fastapi import UploadFile

from app.adapters.mineru_adapter import MinerUAdapter
from app.core.exceptions import AppError
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
        parser=_UnusedParser(),
        adapter=MinerUAdapter(),
        storage=storage,
        asset_service=AssetService(storage),
    )


def _upload(filename: str) -> UploadFile:
    return UploadFile(filename=filename, file=BytesIO(b"%PDF-1.7 fake-bytes"))


async def test_create_job_accepts_pdf(tmp_path: Path) -> None:
    job, source_path = await _service(tmp_path).create_job(_upload("lesson.pdf"))

    assert job.source_file_name == "lesson.pdf"
    assert source_path.suffix == ".pdf"
    assert source_path.is_file()


@pytest.mark.parametrize("filename", ["lesson.doc", "lesson.docx", "lesson.png", "lesson.pptx", "lesson.xlsx"])
async def test_create_job_rejects_non_pdf_documents(tmp_path: Path, filename: str) -> None:
    with pytest.raises(AppError) as exc_info:
        await _service(tmp_path).create_job(_upload(filename))

    assert exc_info.value.code == "UNSUPPORTED_FILE"
    assert exc_info.value.status_code == 415
    assert not any(tmp_path.iterdir())


async def test_create_job_rejects_file_with_pdf_extension_and_invalid_content(tmp_path: Path) -> None:
    upload = UploadFile(filename="lesson.pdf", file=BytesIO(b"not a PDF"))

    with pytest.raises(AppError) as exc_info:
        await _service(tmp_path).create_job(upload)

    assert exc_info.value.code == "UNSUPPORTED_FILE"
    assert exc_info.value.status_code == 415
    assert not any(tmp_path.iterdir())
