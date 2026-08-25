import shutil
from pathlib import Path, PurePosixPath

from app.storage.local_storage import LocalStorage


class AssetService:
    IMAGE_SUFFIXES = {".png", ".jpg", ".jpeg", ".jp2", ".webp", ".gif", ".bmp", ".svg"}

    def __init__(self, storage: LocalStorage) -> None:
        self.storage = storage

    def collect_mineru_assets(self, job_id: str, result_dir: Path) -> dict[str, str]:
        assets_dir = self.storage.job_dir(job_id) / "assets"
        assets_dir.mkdir(parents=True, exist_ok=True)
        mapping: dict[str, str] = {}
        for index, source in enumerate(sorted(result_dir.rglob("*")), start=1):
            if not source.is_file() or source.suffix.lower() not in self.IMAGE_SUFFIXES:
                continue
            safe_name = f"{index:04d}-{source.name}"
            target = assets_dir / safe_name
            shutil.copy2(source, target)
            url = f"/api/assets/{job_id}/{safe_name}"
            relative = source.relative_to(result_dir).as_posix()
            mapping[relative] = url
            mapping[PurePosixPath(relative).name] = url
            if "images/" in relative:
                mapping[relative[relative.index("images/"):]] = url
        return mapping

    def get_asset(self, job_id: str, filename: str) -> Path:
        return self.storage.resolve_asset(job_id, filename)
