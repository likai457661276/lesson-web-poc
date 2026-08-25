from fastapi import APIRouter, Depends
from fastapi.responses import FileResponse

from app.dependencies import get_asset_service
from app.services.asset_service import AssetService

router = APIRouter(prefix="/api/assets", tags=["assets"])


@router.get("/{job_id}/{filename}", response_class=FileResponse)
def get_asset(
    job_id: str,
    filename: str,
    service: AssetService = Depends(get_asset_service),
) -> FileResponse:
    return FileResponse(service.get_asset(job_id, filename))
