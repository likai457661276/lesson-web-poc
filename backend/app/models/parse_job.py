from datetime import datetime, timezone
from enum import StrEnum

from pydantic import Field

from app.models.lesson_document import ApiModel, LessonDocument


class JobStatus(StrEnum):
    PENDING = "pending"
    PROCESSING = "processing"
    COMPLETED = "completed"
    FAILED = "failed"


class ErrorDetail(ApiModel):
    code: str
    message: str


class ParseJob(ApiModel):
    job_id: str = Field(alias="jobId")
    status: JobStatus
    source_file_name: str = Field(alias="sourceFileName")
    created_at: datetime = Field(
        default_factory=lambda: datetime.now(timezone.utc), alias="createdAt"
    )
    document: LessonDocument | None = None
    error: ErrorDetail | None = None
