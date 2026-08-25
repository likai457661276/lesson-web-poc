class AppError(Exception):
    def __init__(self, code: str, message: str, status_code: int = 400) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.status_code = status_code


class MinerUError(AppError):
    def __init__(self, message: str) -> None:
        super().__init__("MINERU_PARSE_FAILED", message, 502)
