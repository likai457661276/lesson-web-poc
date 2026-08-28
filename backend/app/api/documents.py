from urllib.parse import quote

from fastapi import APIRouter, BackgroundTasks, Depends, File, UploadFile, status
from fastapi.responses import StreamingResponse

from app.dependencies import get_document_service, get_docx_export_service
from app.models.docx_export import DocxExportRequest
from app.models.parse_job import ParseJob
from app.services.document_service import DocumentService
from app.services.docx_export_service import DocxExportService

router = APIRouter(prefix="/api/documents", tags=["documents"])


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
