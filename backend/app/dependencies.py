from functools import lru_cache

from app.adapters.mineru_adapter import MinerUAdapter
from app.core.config import settings
from app.parsers.mineru_parser import MinerUDocumentParser
from app.services.asset_service import AssetService
from app.services.document_service import DocumentService
from app.storage.local_storage import LocalStorage


@lru_cache
def get_storage() -> LocalStorage:
    return LocalStorage(settings.data_dir, settings.max_file_size_mb)


@lru_cache
def get_asset_service() -> AssetService:
    return AssetService(get_storage())


@lru_cache
def get_document_service() -> DocumentService:
    return DocumentService(
        settings=settings,
        parser=MinerUDocumentParser(settings),
        adapter=MinerUAdapter(),
        storage=get_storage(),
        asset_service=get_asset_service(),
    )
