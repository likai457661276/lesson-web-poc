from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field


class ApiModel(BaseModel):
    model_config = ConfigDict(populate_by_name=True, serialize_by_alias=True)


class LessonBlockBase(ApiModel):
    id: str
    source_page: int | None = Field(default=None, alias="sourcePage", ge=1)
    group_id: str | None = Field(default=None, alias="groupId")
    review_required: bool = Field(default=False, alias="reviewRequired")
    review_reason: str | None = Field(default=None, alias="reviewReason")


class HeadingBlock(LessonBlockBase):
    type: Literal["heading"]
    level: int = Field(ge=1, le=6)
    text: str
    alignment: Literal["left", "center", "right"]


class ParagraphBlock(LessonBlockBase):
    type: Literal["paragraph"]
    text: str


class ListBlock(LessonBlockBase):
    type: Literal["list"]
    items: list[str]
    ordered: bool = False


class TableBlock(LessonBlockBase):
    type: Literal["table"]
    html: str


class ImageBlock(LessonBlockBase):
    type: Literal["image"]
    src: str
    alt: str | None = None


class FormulaBlock(LessonBlockBase):
    type: Literal["formula"]
    latex: str


LessonBlock = Annotated[
    HeadingBlock | ParagraphBlock | ListBlock | TableBlock | ImageBlock | FormulaBlock,
    Field(discriminator="type"),
]


class LessonMetadata(ApiModel):
    source_type: str = Field(alias="sourceType")
    source_file_name: str = Field(alias="sourceFileName")
    source_url: str | None = Field(default=None, alias="sourceUrl")


class LessonDocument(ApiModel):
    version: Literal["1.0"] = "1.0"
    document_id: str = Field(alias="documentId")
    title: str
    metadata: LessonMetadata
    blocks: list[LessonBlock]
