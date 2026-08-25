from abc import ABC, abstractmethod
from pathlib import Path
from typing import Any


class DocumentParser(ABC):
    @abstractmethod
    async def parse(self, file_path: Path) -> dict[str, Any]:
        """Parse a local source file and return provider-native data."""
