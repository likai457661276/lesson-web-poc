from app.adapters.mineru_adapter import MinerUAdapter


def test_adapter_maps_all_six_block_types() -> None:
    raw = {
        "content_list": [
            {"type": "text", "text_level": 1, "text": "勾股定理"},
            {"type": "text", "text": "理解定理。"},
            {"type": "list", "items": ["观察", "证明"]},
            {
                "type": "table",
                "table_body": '<table><tr><td><img src="images/a.png">活动</td></tr></table>',
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
