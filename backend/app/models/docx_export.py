from pydantic import BaseModel, Field


class DocxExportRequest(BaseModel):
    html: str = Field(min_length=1, max_length=25_000_000)
    filename: str = Field(default="lesson.docx", min_length=1, max_length=180)
