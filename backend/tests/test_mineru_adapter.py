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
    assert document.blocks[0].alignment == "left"


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


def test_adapter_infers_heading_alignment_from_geometry_not_text() -> None:
    raw = {
        "content_list": [
            {"type": "text", "text_level": 1, "text": "文档主标题", "bbox": [430, 80, 570, 112]},
            {"type": "text", "text_level": 2, "text": "2.4 通用章节", "bbox": [390, 130, 610, 160]},
            {"type": "text", "text_level": 2, "text": "左对齐小节", "bbox": [145, 175, 280, 200]},
            {"type": "text", "text_level": 2, "text": "右侧标题", "bbox": [735, 220, 880, 245]},
        ]
    }

    document = MinerUAdapter().convert(raw, document_id="job-alignment", source_file_name="arbitrary.pdf")

    assert [block.alignment for block in document.blocks] == ["center", "center", "left", "right"]


def test_adapter_restores_heading_run_gaps_from_ocr_geometry() -> None:
    raw = {
        "content_list": [
            {"type": "text", "text_level": 2, "text": "2.4 通用章节", "page_idx": 0, "bbox": [390, 130, 610, 160]},
        ],
        "ocr_layout": [[
            {"bbox": [0.39, 0.13, 0.46, 0.16]},
            {"bbox": [0.51, 0.13, 0.61, 0.16]},
        ]],
    }

    document = MinerUAdapter().convert(raw, document_id="job-spacing", source_file_name="arbitrary.pdf")

    assert document.blocks[0].text == "2.4　　通用章节"


def test_adapter_keeps_heading_text_when_ocr_runs_are_ambiguous() -> None:
    raw = {
        "content_list": [
            {"type": "text", "text_level": 2, "text": "A regular heading", "page_idx": 0, "bbox": [100, 100, 400, 140]},
        ],
        "ocr_layout": [[{"bbox": [0.1, 0.1, 0.4, 0.14]}]],
    }

    document = MinerUAdapter().convert(raw, document_id="job-spacing-fallback", source_file_name="arbitrary.pdf")

    assert document.blocks[0].text == "A regular heading"


def test_adapter_defaults_heading_alignment_when_geometry_is_missing() -> None:
    raw = {"content_list": [{"type": "title", "text": "无版面信息标题"}]}

    document = MinerUAdapter().convert(raw, document_id="job-no-layout", source_file_name="plain.pdf")

    assert document.blocks[0].alignment == "left"


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
