import uuid
from io import BytesIO
from zipfile import ZipFile

from docx import Document
from fastapi.testclient import TestClient
from fontTools.ttLib import TTFont
from lxml import etree

from app.main import app


client = TestClient(app)


def _read_embedded_font(archive: ZipFile, style: str) -> TTFont:
    font_table = etree.fromstring(archive.read("word/fontTable.xml"))
    namespaces = {
        "w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main",
    }
    element_name = "embedRegular" if style == "regular" else "embedBold"
    font_key = font_table.xpath(
        f"./w:font[@w:name='Noto Sans SC']/w:{element_name}/@w:fontKey",
        namespaces=namespaces,
    )[0]
    font_data = bytearray(archive.read(f"word/fonts/NotoSansSC-{style}.odttf"))
    key = uuid.UUID(font_key.strip("{}")).bytes[::-1]
    for index in range(min(32, len(font_data))):
        font_data[index] ^= key[index % len(key)]
    return TTFont(BytesIO(font_data))


def test_export_docx_converts_supported_html() -> None:
    response = client.post(
        "/api/documents/export-docx",
        json={
            "filename": "三角函数教案.docx",
            "html": """
                <article>
                  <h1 data-docx-title>三角函数教案</h1>
                  <h2>学习目标</h2>
                  <p>掌握 <strong>正弦函数</strong> 与 <em>余弦函数</em>。</p>
                  <ol><li>复习定义</li><li>完成练习</li></ol>
                  <p><span data-latex="\\sin^2 x + \\cos^2 x = 1"></span></p>
                  <table><tr><th>角度</th><th>正弦值</th></tr><tr><td>范围：<span data-latex="360^{\\circ} \\leq b &lt; 720^{\\circ}"></span> 的元素</td><td>1/2</td></tr></table>
                  <script>alert('ignored')</script>
                </article>
            """,
        },
    )

    assert response.status_code == 200
    assert response.headers["content-type"].startswith(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    )
    assert "filename*=UTF-8''" in response.headers["content-disposition"]

    document = Document(BytesIO(response.content))
    text = "\n".join(paragraph.text for paragraph in document.paragraphs)
    assert "三角函数教案" in text
    assert "学习目标" in text
    assert "正弦函数" in text
    assert r"\sin^2 x + \cos^2 x = 1" not in text
    assert "alert" not in text
    assert len(document.tables) == 1
    assert document.tables[0].cell(1, 0).text == "范围： 的元素"
    assert document.tables[0].cell(1, 1).text == "1/2"

    with ZipFile(BytesIO(response.content)) as archive:
        content_types_xml = archive.read("[Content_Types].xml").decode()
        document_xml = archive.read("word/document.xml").decode()
        font_table_xml = archive.read("word/fontTable.xml").decode()
        font_rels_xml = archive.read("word/_rels/fontTable.xml.rels").decode()
        numbering_xml = archive.read("word/numbering.xml").decode()
        settings_xml = archive.read("word/settings.xml").decode()
        styles_xml = archive.read("word/styles.xml").decode()
        embedded_font_parts = sorted(
            name for name in archive.namelist() if name.startswith("word/fonts/")
        )
        regular_font = _read_embedded_font(archive, "regular")
        bold_font = _read_embedded_font(archive, "bold")
    assert "w:numPr" in document_xml
    assert "w:tblHeader" in document_xml
    assert document_xml.count("<m:oMath>") == 2
    assert "<m:sSup>" in document_xml
    assert "<m:t>≤</m:t>" in document_xml
    assert "<m:t>∘</m:t>" in document_xml
    assert "\\sin^2" not in document_xml
    assert "360^{" not in document_xml
    assert "multilevel" in numbering_xml
    assert 'w:eastAsia="zh-CN"' in styles_xml
    assert 'w:eastAsia="Noto Sans SC"' in styles_xml
    assert embedded_font_parts == [
        "word/fonts/NotoSansSC-bold.odttf",
        "word/fonts/NotoSansSC-regular.odttf",
    ]
    assert 'w:name="Noto Sans SC"' in font_table_xml
    assert 'w:altName w:val="Microsoft YaHei"' in font_table_xml
    assert "w:embedRegular" in font_table_xml
    assert "w:embedBold" in font_table_xml
    assert font_rels_xml.count("/relationships/font") == 2
    assert 'Extension="odttf"' in content_types_xml
    assert "w:embedTrueTypeFonts" in settings_xml
    assert "w:saveSubsetFonts" in settings_xml
    assert ord("三") in regular_font.getBestCmap()
    assert ord("教") in bold_font.getBestCmap()
    assert regular_font["OS/2"].fsType == 0
    assert bold_font["OS/2"].fsType == 0


def test_export_docx_keeps_invalid_latex_visible() -> None:
    response = client.post(
        "/api/documents/export-docx",
        json={"filename": "invalid-formula.docx", "html": '<p><span data-latex="\\frac{"></span></p>'},
    )

    assert response.status_code == 200
    document = Document(BytesIO(response.content))
    assert document.paragraphs[0].text == r"\frac{"


def test_export_docx_rejects_html_without_content() -> None:
    response = client.post(
        "/api/documents/export-docx",
        json={"filename": "empty.docx", "html": "<script>alert(1)</script>"},
    )

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "EMPTY_HTML"


def test_export_docx_embeds_data_url_image() -> None:
    response = client.post(
        "/api/documents/export-docx",
        json={
            "filename": "image.docx",
            "html": (
                '<p>Image</p><img alt="pixel" '
                'src="data:image/png;base64,'
                'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="/>'
            ),
        },
    )

    assert response.status_code == 200
    assert len(Document(BytesIO(response.content)).inline_shapes) == 1


def test_export_docx_distributes_colspan_content_across_merged_columns() -> None:
    response = client.post(
        "/api/documents/export-docx",
        json={
            "filename": "layout-table.docx",
            "html": """
                <table class="lesson-layout-table" data-repeat-header="false">
                  <tr><td colspan="4"><strong>一、内容</strong></td></tr>
                  <tr><td>A</td><td>B</td><td colspan="2">这是需要在合并单元格中获得足够宽度显示的子问题内容，列宽计算不能忽略跨列单元格中的长文本内容</td></tr>
                </table>
            """,
        },
    )

    assert response.status_code == 200
    document = Document(BytesIO(response.content))
    assert len(document.tables) == 1
    with ZipFile(BytesIO(response.content)) as archive:
        document_xml = archive.read("word/document.xml").decode()
    assert "w:tblHeader" not in document_xml

    grid = document.tables[0]._tbl.tblGrid
    widths = [int(column.get("{http://schemas.openxmlformats.org/wordprocessingml/2006/main}w")) for column in grid]
    assert widths[2] > widths[0]
    assert widths[3] > widths[1]
