from pathlib import Path
from urllib.parse import quote

from fastapi import APIRouter, BackgroundTasks, Depends, File, UploadFile, status
from fastapi.responses import FileResponse, StreamingResponse

from app.dependencies import get_document_service, get_docx_export_service
from app.models.docx_export import DocxExportRequest
from app.models.parse_job import ParseJob
from app.services.document_service import DocumentService
from app.services.docx_export_service import DocxExportService

router = APIRouter(prefix="/api/documents", tags=["documents"])

SOURCE_MEDIA_TYPES = {
    ".pdf": "application/pdf",
    ".doc": "application/msword",
    ".docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    ".ppt": "application/vnd.ms-powerpoint",
    ".pptx": "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    ".xls": "application/vnd.ms-excel",
    ".xlsx": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    ".png": "image/png",
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".jp2": "image/jp2",
    ".webp": "image/webp",
    ".gif": "image/gif",
    ".bmp": "image/bmp",
}
INLINE_SOURCE_SUFFIXES = {".pdf", ".png", ".jpg", ".jpeg", ".jp2", ".webp", ".gif", ".bmp"}


@router.post("/parse", response_model=ParseJob, status_code=status.HTTP_202_ACCEPTED)
async def parse_document(
    background_tasks: BackgroundTasks,
    file: UploadFile = File(...),
    service: DocumentService = Depends(get_document_service),
) -> ParseJob:
    job, source_path = await service.create_job(file)
    background_tasks.add_task(service.process_job, job.job_id, source_path)
    return job


@router.post("/export-docx", response_class=StreamingResponse)
def export_docx(
    request: DocxExportRequest,
    service: DocxExportService = Depends(get_docx_export_service),
) -> StreamingResponse:
    content, filename = service.export(request.html, request.filename)
    encoded_filename = quote(filename)
    return StreamingResponse(
        content,
        media_type="application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        headers={
            "Content-Disposition": (
                f'attachment; filename="lesson.docx"; filename*=UTF-8\'\'{encoded_filename}'
            )
        },
    )


@router.get("/{job_id}", response_model=ParseJob)
def get_document(
    job_id: str,
    service: DocumentService = Depends(get_document_service),
) -> ParseJob:
    return service.get_job(job_id)


@router.get("/{job_id}/source", response_class=FileResponse)
def get_document_source(
    job_id: str,
    service: DocumentService = Depends(get_document_service),
) -> FileResponse:
    path = service.get_source(job_id)
    suffix = Path(path).suffix.lower()
    return FileResponse(
        path,
        filename=service.get_job(job_id).source_file_name,
        media_type=SOURCE_MEDIA_TYPES.get(suffix),
        content_disposition_type="inline" if suffix in INLINE_SOURCE_SUFFIXES else "attachment",
    )
