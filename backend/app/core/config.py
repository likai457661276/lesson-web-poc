from functools import lru_cache
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "Lesson Web PoC API"
    app_env: str = "local"
    app_host: str = "0.0.0.0"
    app_port: int = 10011
    log_level: str = "INFO"
    frontend_origins: str = "http://localhost:5173,http://127.0.0.1:5173"

    data_dir: Path = Path("../data/jobs")
    max_file_size_mb: int = 200
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
    def frontend_origin_list(self) -> list[str]:
        return [origin.strip() for origin in self.frontend_origins.split(",") if origin.strip()]

@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
