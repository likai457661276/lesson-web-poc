import json
from pathlib import Path

from app.parsers.mineru_parser import MinerUDocumentParser


def test_parser_keeps_only_ocr_boxes_from_model_file(tmp_path: Path) -> None:
    model = [[
        {"type": "paragraph_title", "bbox": [0.1, 0.1, 0.4, 0.2], "content": None},
        {"type": "ocr_text", "bbox": [0.1, 0.1, 0.2, 0.2], "text": "", "score": 1.0},
        {"type": "ocr_text", "bbox": [0.3, 0.1, 0.4, 0.2], "text": "", "score": 1.0},
    ]]
    (tmp_path / "document_model.json").write_text(
        json.dumps(model),
        encoding="utf-8",
    )

    result = MinerUDocumentParser._load_ocr_layout(tmp_path)

    assert result == [[
        {"bbox": [0.1, 0.1, 0.2, 0.2]},
        {"bbox": [0.3, 0.1, 0.4, 0.2]},
    ]]


def test_parser_ocr_layout_is_optional(tmp_path: Path) -> None:
    assert MinerUDocumentParser._load_ocr_layout(tmp_path) == []


def test_parser_loads_page_local_table_html(tmp_path: Path) -> None:
    model = [
        [{"type": "table", "bbox": [0.1, 0.1, 0.9, 0.9], "content": "<table><tr><td>第一页</td></tr></table>"}],
        [{"type": "table", "bbox": [0.1, 0.05, 0.9, 0.8], "content": "<table><tr><td>第二页</td></tr></table>"}],
    ]
    (tmp_path / "document_model.json").write_text(json.dumps(model), encoding="utf-8")

    result = MinerUDocumentParser._load_page_tables(tmp_path)

    assert result[0][0]["page_idx"] == 0
    assert "第一页" in result[0][0]["table_body"]
    assert result[1][0]["page_idx"] == 1
