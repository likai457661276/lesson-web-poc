import pytest
from pydantic import ValidationError

from app.models.lesson_document import LessonDocument


def test_lesson_document_uses_camel_case_contract() -> None:
    document = LessonDocument.model_validate(
        {
            "version": "1.0",
            "documentId": "lesson-1",
            "title": "勾股定理",
            "metadata": {
                "sourceType": "docx",
                "sourceFileName": "lesson.docx",
            },
            "blocks": [
                {"id": "1", "type": "heading", "level": 1, "text": "教学目标", "alignment": "left"},
                {"id": "2", "type": "formula", "latex": "a^2+b^2=c^2"},
            ],
        }
    )

    payload = document.model_dump(by_alias=True)
    assert payload["documentId"] == "lesson-1"
    assert payload["metadata"]["sourceFileName"] == "lesson.docx"


def test_unknown_block_type_is_rejected() -> None:
    with pytest.raises(ValidationError):
        LessonDocument.model_validate(
            {
                "documentId": "lesson-1",
                "title": "test",
                "metadata": {"sourceType": "pdf", "sourceFileName": "test.pdf"},
                "blocks": [{"id": "1", "type": "mineru_text", "text": "raw"}],
            }
        )
