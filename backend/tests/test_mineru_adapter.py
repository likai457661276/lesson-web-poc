from app.adapters.mineru_adapter import MinerUAdapter


def test_adapter_maps_all_six_block_types() -> None:
    raw = {
        "content_list": [
            {"type": "text", "text_level": 1, "text": "勾股定理"},
            {"type": "text", "text": "理解定理。"},
            {"type": "list", "items": ["观察", "证明"]},
            {
                "type": "table",
                "table_body": '<table><tr><td><img src="images/a.png">$-360^{\\circ} \\leq b$</td></tr></table>',
            },
            {"type": "image", "img_path": "images/a.png", "image_caption": ["示意图"]},
            {"type": "interline_equation", "text": "a^2+b^2=c^2"},
        ]
    }

    document = MinerUAdapter().convert(
        raw,
        document_id="job-1",
        source_file_name="lesson.docx",
        asset_urls={"images/a.png": "/api/assets/job-1/a.png"},
    )

    assert document.title == "勾股定理"
    assert [block.type for block in document.blocks] == [
        "heading",
        "paragraph",
        "list",
        "table",
        "image",
        "formula",
    ]
    assert document.blocks[4].src == "/api/assets/job-1/a.png"
    assert '/api/assets/job-1/a.png' in document.blocks[3].html
    assert 'data-latex="-360^{\\circ} \\leq b"' in document.blocks[3].html


def test_adapter_strips_heading_markup_and_normalizes_degrees() -> None:
    raw = {
        "content_list": [
            {"type": "title", "text": "第一<sub>课时</sub>"},
            {"type": "formula", "latex": "30°"},
        ]
    }

    document = MinerUAdapter().convert(
        raw,
        document_id="job-2",
        source_file_name="lesson.pdf",
    )

    assert document.title == "第一课时"
    assert document.blocks[1].latex == r"30^{\circ}"


def test_adapter_restores_text_rows_belonging_to_layout_table() -> None:
    raw = {
        "content_list": [
            {"type": "text", "text_level": 1, "text": "第一课时", "page_idx": 0, "bbox": [430, 80, 570, 115]},
            {"type": "text", "text_level": 2, "text": "一、内容", "page_idx": 0, "bbox": [144, 170, 240, 195]},
            {"type": "text", "text": "任意角的概念", "page_idx": 0, "bbox": [142, 205, 550, 230]},
            {"type": "text", "text_level": 2, "text": "二、教学策略", "page_idx": 0, "bbox": [144, 240, 280, 263]},
            {"type": "text", "text": "形成问题链", "page_idx": 0, "bbox": [178, 268, 850, 291]},
            {
                "type": "table",
                "page_idx": 0,
                "bbox": [147, 290, 875, 900],
                "table_body": '<table><tr><td>核心问题</td><td>主干问题</td><td colspan="2">$b &lt; 720$</td></tr></table>',
            },
        ]
    }

    document = MinerUAdapter().convert(raw, document_id="job-layout", source_file_name="lesson.pdf")

    assert [block.type for block in document.blocks] == ["heading", "table"]
    table_html = document.blocks[1].html
    assert 'class="lesson-layout-table"' in table_html
    assert 'data-repeat-header="false"' in table_html
    assert table_html.index("一、内容") < table_html.index("任意角的概念") < table_html.index("核心问题")
    assert 'class="lesson-layout-cell lesson-layout-heading-cell"' in table_html
    assert 'class="lesson-layout-cell lesson-layout-centered-cell"' in table_html
    assert 'data-latex="b &lt; 720"' in table_html
    assert "&amp;lt;" not in table_html


def test_adapter_does_not_merge_unaligned_paragraphs_into_regular_table() -> None:
    raw = {
        "content_list": [
            {"type": "text", "text_level": 2, "text": "数据分析", "page_idx": 0, "bbox": [100, 100, 300, 125]},
            {"type": "text", "text": "下面是统计结果。", "page_idx": 0, "bbox": [100, 140, 420, 165]},
            {"type": "table", "page_idx": 0, "bbox": [300, 300, 700, 500], "table_body": "<table><tr><td>A</td></tr></table>"},
        ]
    }

    document = MinerUAdapter().convert(raw, document_id="job-regular", source_file_name="lesson.pdf")

    assert [block.type for block in document.blocks] == ["heading", "paragraph", "table"]
