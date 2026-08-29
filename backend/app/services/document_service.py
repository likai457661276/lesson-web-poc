import logging
from datetime import datetime, timezone
from pathlib import Path
from time import perf_counter
from uuid import uuid4

from fastapi import UploadFile

from app.adapters.mineru_adapter import MinerUAdapter
from app.core.exceptions import AppError
from app.models.lesson_document import FormulaBlock, ImageBlock, TableBlock
from app.models.parse_job import ErrorDetail, JobStatus, ParseJob
from app.parsers.base import DocumentParser
from app.services.asset_service import AssetService
from app.storage.local_storage import LocalStorage

logger = logging.getLogger(__name__)


class DocumentService:
    PDF_SIGNATURE = b"%PDF-"

    def __init__(
        self,
        *,
        parser: DocumentParser,
        adapter: MinerUAdapter,
        storage: LocalStorage,
        asset_service: AssetService,
    ) -> None:
        self.parser = parser
        self.adapter = adapter
        self.storage = storage
        self.asset_service = asset_service
        self.jobs: dict[str, ParseJob] = {}

    async def create_job(self, upload: UploadFile) -> tuple[ParseJob, Path]:
        filename = Path(upload.filename or "").name
        extension = Path(filename).suffix.lower()
        if not filename or extension != ".pdf" or not await self._is_pdf(upload):
            await upload.close()
            raise AppError("UNSUPPORTED_FILE", "仅支持 PDF 格式文件", 415)
        job_id = str(uuid4())
        source_path = await self.storage.save_upload(job_id, upload)
        job = ParseJob(job_id=job_id, status=JobStatus.PENDING, source_file_name=filename)
        self.jobs[job_id] = job
        return job, source_path

    async def _is_pdf(self, upload: UploadFile) -> bool:
        header = await upload.read(len(self.PDF_SIGNATURE))
        await upload.seek(0)
        return header == self.PDF_SIGNATURE

    async def process_job(self, job_id: str, source_path: Path) -> None:
        job = self.get_job(job_id)
        job.status = JobStatus.PROCESSING
        started_at = datetime.now(timezone.utc)
        timer = perf_counter()
        try:
            raw_result = await self.parser.parse(source_path)
            self.storage.save_provider_result(job_id, raw_result)
            asset_urls = self.asset_service.collect_mineru_assets(
                job_id, Path(raw_result["result_dir"])
            )
            document = self.adapter.convert(
                raw_result,
                document_id=job_id,
                source_file_name=job.source_file_name,
                asset_urls=asset_urls,
            )
            self.storage.save_document(job_id, document)
            job.document = document
            job.status = JobStatus.COMPLETED
            logger.info(
                "document_parse_completed",
                extra={
                    "jobId": job_id,
                    "fileName": job.source_file_name,
                    "fileType": source_path.suffix.lstrip("."),
                    "fileSize": source_path.stat().st_size,
                    "parseStartedAt": started_at.isoformat(),
                    "parseFinishedAt": datetime.now(timezone.utc).isoformat(),
                    "parseDuration": round(perf_counter() - timer, 3),
                    "blockCount": len(document.blocks),
                    "imageCount": sum(isinstance(block, ImageBlock) for block in document.blocks),
                    "tableCount": sum(isinstance(block, TableBlock) for block in document.blocks),
                    "formulaCount": sum(isinstance(block, FormulaBlock) for block in document.blocks),
                    "status": job.status,
                },
            )
        except Exception as exc:
            code = exc.code if isinstance(exc, AppError) else "ADAPTER_CONVERT_FAILED"
            message = exc.message if isinstance(exc, AppError) else "文档转换失败"
            job.status = JobStatus.FAILED
            job.error = ErrorDetail(code=code, message=message)
            logger.exception(
                "document_parse_failed",
                extra={
                    "jobId": job_id,
                    "fileName": job.source_file_name,
                    "status": job.status,
                    "errorCode": code,
                },
            )

    def get_job(self, job_id: str) -> ParseJob:
        job = self.jobs.get(job_id)
        if job is None:
            raise AppError("JOB_NOT_FOUND", "解析任务不存在", 404)
        return job
