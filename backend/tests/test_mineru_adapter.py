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
    assert "lesson-layout-table" in table_html
    assert 'data-column-count="4"' in table_html
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


def test_adapter_restores_page_local_tables_and_source_groups() -> None:
    raw = {
        "content_list": [
            {"type": "table", "page_idx": 0, "bbox": [100, 100, 900, 900], "table_body": "<table><tr><td>跨页合并内容</td></tr></table>"},
            {"type": "table", "page_idx": 1, "bbox": [100, 80, 900, 900], "table_body": ""},
        ],
        "page_tables": [
            [{"type": "table", "page_idx": 0, "bbox": [0.1, 0.1, 0.9, 0.9], "table_body": "<table><tr><td>第一页</td></tr></table>"}],
            [{"type": "table", "page_idx": 1, "bbox": [0.1, 0.08, 0.9, 0.9], "table_body": "<table><tr><td>第二页</td></tr></table>"}],
        ],
    }

    document = MinerUAdapter().convert(raw, document_id="job-pages", source_file_name="pages.pdf")

    assert [block.source_page for block in document.blocks] == [1, 2]
    assert [block.group_id for block in document.blocks] == ["page-1", "page-2"]
    assert "第一页" in document.blocks[0].html
    assert "第二页" in document.blocks[1].html
    assert "跨页合并内容" not in document.blocks[0].html


def test_adapter_flags_cross_column_text_and_removes_proven_duplicate_suffix() -> None:
    raw = {
        "content_list": [
            {"type": "text", "page_idx": 0, "bbox": [50, 100, 600, 300], "text": "左栏的正常长段落。"},
            {"type": "text", "page_idx": 0, "bbox": [730, 100, 900, 800], "text": "右栏完整说明以及重复结尾。"},
            {"type": "text", "page_idx": 0, "bbox": [50, 760, 860, 810], "text": "左栏下一段误带重复结尾。"},
        ]
    }

    document = MinerUAdapter().convert(raw, document_id="job-columns", source_file_name="columns.pdf")

    suspect = document.blocks[2]
    assert suspect.review_required is True
    assert suspect.text == "左栏下一段误带"


def test_adapter_filters_small_captioned_header_image_but_keeps_teaching_image() -> None:
    raw = {
        "content_list": [
            {"type": "image", "page_idx": 0, "bbox": [220, 90, 290, 150], "img_path": "images/logo.png", "image_caption": ["页眉标题"]},
            {"type": "image", "page_idx": 1, "bbox": [216, 420, 285, 470], "img_path": "images/logo-continued.png", "image_caption": ["分区标题"]},
            {"type": "image", "page_idx": 0, "bbox": [200, 300, 800, 700], "img_path": "images/diagram.png", "image_caption": ["教学示意图"]},
        ]
    }

    document = MinerUAdapter().convert(
        raw,
        document_id="job-images",
        source_file_name="images.pdf",
        asset_urls={"logo.png": "/logo.png", "logo-continued.png": "/logo-continued.png", "diagram.png": "/diagram.png"},
    )

    assert len(document.blocks) == 1
    assert document.blocks[0].src == "/diagram.png"


def test_adapter_recovers_compact_plain_text_equations_in_tables() -> None:
    raw = {
        "content_list": [
            {
                "type": "table",
                "page_idx": 0,
                "table_body": "<table><tr><td>练习\n12:x=6:4 1/24:5/6=x:9</td><td>说明</td><td>结论</td></tr></table>",
            }
        ]
    }

    document = MinerUAdapter().convert(raw, document_id="job-math", source_file_name="math.pdf")
    table_html = document.blocks[0].html

    assert 'data-latex="12:x=6:4"' in table_html
    assert 'data-latex="\\frac{1}{24}:\\frac{5}{6}=x:9"' in table_html
    assert 'data-column-count="3"' in table_html
