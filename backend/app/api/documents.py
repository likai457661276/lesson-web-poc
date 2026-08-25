from fastapi import APIRouter, BackgroundTasks, Depends, File, UploadFile, status

from app.dependencies import get_document_service
from app.models.parse_job import ParseJob
from app.services.document_service import DocumentService

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


@router.get("/{job_id}", response_model=ParseJob)
def get_document(
    job_id: str,
    service: DocumentService = Depends(get_document_service),
) -> ParseJob:
    return service.get_job(job_id)
