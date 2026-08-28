from functools import lru_cache
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "Lesson Web PoC API"
    app_env: str = "local"
    app_host: str = "0.0.0.0"
    app_port: int = 8000
    log_level: str = "INFO"
    frontend_origin: str = "http://localhost:5173"

    data_dir: Path = Path("../data/jobs")
    max_file_size_mb: int = 200
    allowed_extensions: str = ".pdf,.png,.jpg,.jpeg,.jp2,.webp,.gif,.bmp,.ppt,.pptx,.xls,.xlsx"

    mineru_api_key: str = Field(default="", repr=False)
    mineru_base_url: str = "https://mineru.net/api/v4"
    mineru_model_version: str = "vlm"
    mineru_language: str = "ch"
    mineru_poll_interval_seconds: float = 3.0
    mineru_timeout_seconds: int = 600

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    @property
    def allowed_extension_set(self) -> set[str]:
        return {
            item.strip().lower()
            for item in self.allowed_extensions.split(",")
            if item.strip()
        }


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
