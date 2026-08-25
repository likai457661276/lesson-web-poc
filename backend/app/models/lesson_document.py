from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field


class ApiModel(BaseModel):
    model_config = ConfigDict(populate_by_name=True, serialize_by_alias=True)


class HeadingBlock(ApiModel):
    id: str
    type: Literal["heading"]
    level: int = Field(ge=1, le=6)
    text: str
    alignment: Literal["left", "center", "right"]


class ParagraphBlock(ApiModel):
    id: str
    type: Literal["paragraph"]
    text: str


class ListBlock(ApiModel):
    id: str
    type: Literal["list"]
    items: list[str]
    ordered: bool = False


class TableBlock(ApiModel):
    id: str
    type: Literal["table"]
    html: str


class ImageBlock(ApiModel):
    id: str
    type: Literal["image"]
    src: str
    alt: str | None = None


class FormulaBlock(ApiModel):
    id: str
    type: Literal["formula"]
    latex: str


LessonBlock = Annotated[
    HeadingBlock | ParagraphBlock | ListBlock | TableBlock | ImageBlock | FormulaBlock,
    Field(discriminator="type"),
]


class LessonMetadata(ApiModel):
    source_type: str = Field(alias="sourceType")
    source_file_name: str = Field(alias="sourceFileName")


class LessonDocument(ApiModel):
    version: Literal["1.0"] = "1.0"
    document_id: str = Field(alias="documentId")
    title: str
    metadata: LessonMetadata
    blocks: list[LessonBlock]
