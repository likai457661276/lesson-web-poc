package com.lessonweb.lesson.docx;

import com.lessonweb.lesson.docx.formula.LatexToMathmlConverter;
import com.lessonweb.lesson.docx.formula.MathmlToOmmlConverter;
import com.lessonweb.lesson.exception.AppException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HtmlToDocxServiceTest {

    private HtmlToDocxService service;

    @BeforeEach
    void setUp() {
        LatexToMathmlConverter latex = new LatexToMathmlConverter();
        DocxFormulaRenderer formulas = new DocxFormulaRenderer(latex, new MathmlToOmmlConverter());
        service = new HtmlToDocxService(formulas, new DocxImageRenderer(), new DocxFontService());
    }

    @Test
    void preservesParagraphsAndListItemsInsideCells() throws Exception {
        var xml = documentXml("<table><tr><td><p>Observation</p><p>Conclusion</p>"
                + "<ol><li>Inspect</li><li>Measure</li></ol></td></tr></table>");
        var cell = xml.getElementsByTag("w:tc").first();
        assertThat(cell.getElementsByTag("w:p")).hasSize(4);
        assertThat(cell.getElementsByTag("w:p").stream().map(p -> String.join("", p.getElementsByTag("w:t").eachText())).toList())
                .containsExactly("Observation", "Conclusion", "Inspect", "Measure");
        assertThat(cell.getElementsByTag("w:numPr")).hasSize(2);
    }

    @Test
    void nestsTablesWithinCellWidthWithoutDuplicatingContentOrCaptions() throws Exception {
        var xml = documentXml("<table><tr><td>Before<table><caption>Inner caption</caption>"
                + "<tr><td>North</td><td>South</td></tr></table><p>After</p></td><td>Sibling</td></tr></table>");
        assertThat(xml.getElementsByTag("w:tbl")).hasSize(2);
        assertThat(xml.getElementsByTag("w:tr")).hasSize(2);
        assertThat(xml.getElementsByTag("w:t").eachText()).containsExactly("Before", "Inner caption", "North", "South", "After", "Sibling");
        var outerCell = xml.getElementsByTag("w:tc").first();
        int available = Integer.parseInt(outerCell.getElementsByTag("w:tcW").first().attr("w:w")) - 280;
        var inner = outerCell.getElementsByTag("w:tbl").first();
        assertThat(inner.getElementsByTag("w:tblInd").first().attr("w:w")).isEqualTo("0");
        assertThat(Integer.parseInt(inner.getElementsByTag("w:tblW").first().attr("w:w"))).isEqualTo(available);
        assertThat(inner.getElementsByTag("w:gridCol").stream().mapToInt(col -> Integer.parseInt(col.attr("w:w"))).sum()).isEqualTo(available);
        assertThat(outerCell.children().last().tagName()).isEqualTo("w:p");
        var endingInTable = documentXml("<table><tr><td><table><tr><td>End</td></tr></table></td></tr></table>");
        assertThat(endingInTable.getElementsByTag("w:tc").first().children().last().tagName()).isEqualTo("w:p");
    }

    @Test
    void givesIndependentAndNestedListsTheirOwnNumberingAndKeepsNodeOrder() throws Exception {
        var parts = unzip(service.export("<ol><li>Alpha<ol><li>Nested</li></ol><p>Continuation</p></li><li>Beta</li></ol>"
                + "<p>Divider</p><ol start='4'><li>Gamma</li></ol><table><tr><td><ol><li>Delta</li></ol></td></tr></table>", "lists").content());
        var xml = Jsoup.parse(text(parts, "word/document.xml"), "", Parser.xmlParser());
        assertThat(xml.getElementsByTag("w:t").eachText()).containsExactly("Alpha", "Nested", "Continuation", "Beta", "Divider", "Gamma", "Delta");
        var ids = xml.getElementsByTag("w:numId").eachAttr("w:val");
        assertThat(ids).hasSize(5);
        assertThat(ids.get(0)).isEqualTo(ids.get(2));
        assertThat(java.util.Set.of(ids.get(0), ids.get(1), ids.get(3), ids.get(4))).hasSize(4);
        var numbering = Jsoup.parse(text(parts, "word/numbering.xml"), "", Parser.xmlParser());
        assertThat(numbering.getElementsByTag("w:startOverride").eachAttr("w:val")).containsExactly("1", "1", "4", "1");
        assertThat(xml.getElementsByTag("w:ilvl").eachAttr("w:val")).containsExactly("0", "1", "0", "0", "0");
        var continuation = xml.getElementsByTag("w:p").stream().filter(p -> p.getElementsByTag("w:t").eachText().contains("Continuation")).findFirst().orElseThrow();
        assertThat(continuation.getElementsByTag("w:numPr")).isEmpty();
        assertThat(continuation.getElementsByTag("w:ind").first().attr("w:left")).isEqualTo("720");
    }

    @Test
    void preservesAllHeadingLevelsAndProtocolWhitespace() throws Exception {
        StringBuilder html = new StringBuilder();
        for (int level = 1; level <= 6; level++) html.append("<h").append(level).append(" class='lesson-heading lesson-heading-right'>Topic  ")
                .append(level).append("</h").append(level).append(">");
        html.append("<p class='lesson-paragraph'>Line  one\r\nLine\ttwo\nEnd</p><pre>A\nB\tC</pre>");
        var parts = unzip(service.export(html.toString(), "headings").content());
        var xml = Jsoup.parse(text(parts, "word/document.xml"), "", Parser.xmlParser());
        assertThat(xml.getElementsByTag("w:pStyle").eachAttr("w:val"))
                .containsExactly("LessonHeading1", "LessonHeading2", "LessonHeading3", "LessonHeading4", "LessonHeading5", "LessonHeading6");
        assertThat(xml.getElementsByTag("w:t").stream().map(org.jsoup.nodes.Element::wholeText).toList())
                .contains("Topic  6", "Line  one", "Line", "two", "End");
        assertThat(xml.getElementsByTag("w:br")).hasSize(3);
        assertThat(xml.getElementsByTag("w:tab")).hasSize(2);
        assertThat(xml.getElementsByTag("w:jc").eachAttr("w:val")).containsOnly("right");
        var styles = Jsoup.parse(text(parts, "word/styles.xml"), "", Parser.xmlParser());
        for (int level = 1; level <= 6; level++) {
            final String id = "LessonHeading" + level;
            var style = styles.getElementsByTag("w:style").stream().filter(s -> s.attr("w:styleId").equals(id)).findFirst().orElseThrow();
            assertThat(style.getElementsByTag("w:outlineLvl").first().attr("w:val")).isEqualTo(Integer.toString(level - 1));
        }
    }

    @Test
    void rejectsUndecodableImagesRegardlessOfPlacementOrAltText() {
        for (String image : new String[]{"<img src='data:image/png;base64,AAAA'>", "<img alt='description' src='data:image/png;base64,broken!'>",
                "<img src='data:image/webp;base64,AAAA'>", "<img src='/missing.png'>"}) {
            for (String html : new String[]{"<p>Valid</p>" + image, "<table><tr><td>Valid" + image + "</td></tr></table>", "<figure>" + image + "</figure>"}) {
                assertThatThrownBy(() -> service.export(html, "invalid-image"))
                        .isInstanceOf(AppException.class)
                        .satisfies(error -> assertThat(((AppException) error).code()).isEqualTo("DOCX_IMAGE_INVALID"));
            }
        }
    }

    private org.jsoup.nodes.Document documentXml(String html) throws Exception {
        return Jsoup.parse(text(unzip(service.export(html, "independent").content()), "word/document.xml"), "", Parser.xmlParser());
    }

    @Test
    void rejectsTablesWithoutRoomForCellContentAndUnsupportedListNumbering() {
        assertThatThrownBy(() -> service.export("<table><tr>" + "<td>x</td>".repeat(40) + "</tr></table>", "wide"))
                .isInstanceOf(AppException.class)
                .satisfies(error -> assertThat(((AppException) error).code()).isEqualTo("DOCX_TABLE_TOO_NARROW"));
        for (String html : new String[]{"<ol reversed><li>x</li></ol>", "<ol start='oops'><li>x</li></ol>",
                "<ol><li value='4'>x</li></ol>", "<ul><li>".repeat(10) + "x" + "</li></ul>".repeat(10)}) {
            assertThatThrownBy(() -> service.export(html, "list"))
                    .isInstanceOf(AppException.class)
                    .satisfies(error -> assertThat(((AppException) error).code()).isEqualTo("DOCX_LIST_UNSUPPORTED"));
        }
    }

    @Test
    void embedsSmallReadableSubsetsWithChineseAndNumberingGlyphs() throws Exception {
        int supplementary;
        try (var input = new ClassPathResource("fonts/NotoSansSC-Regular.ttf").getInputStream();
             var source = new RandomAccessReadBuffer(input); var font = new TTFParser().parse(source)) {
            var cmap = font.getUnicodeCmapLookup();
            supplementary = java.util.stream.IntStream.range(0x20000, 0x30000)
                    .filter(cp -> cmap.getGlyphId(cp) != 0).findFirst().orElseThrow();
        }
        String text = "中文课程é•0123456789\u00a0" + new String(Character.toChars(supplementary));
        var result = service.export("<h1>中文课程</h1><p>" + text + "</p><ol><li>项目</li></ol>", "subset");
        var parts = unzip(result.content());
        assertThat(result.content().length).isLessThan(300_000);
        var table = Jsoup.parse(text(parts, "word/fontTable.xml"), "", Parser.xmlParser());
        for (String style : new String[]{"Regular", "Bold"}) {
            var embedding = table.getElementsByTag("w:embed" + style).first();
            assertThat(embedding.attr("w:subsetted")).isEqualTo("true");
            byte[] key = HexFormat.of().parseHex(embedding.attr("w:fontKey").replaceAll("[{}-]", ""));
            byte[] fontBytes = parts.get("word/fonts/NotoSansSC-" + style.toLowerCase() + ".odttf").clone();
            for (int index = 0; index < 32; index++) fontBytes[index] ^= key[15 - index % 16];
            var displayFont = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, new ByteArrayInputStream(fontBytes)).deriveFont(24f);
            assertThat(displayFont.canDisplay('中')).isTrue();
            var context = new java.awt.font.FontRenderContext(null, true, true);
            assertThat(displayFont.createGlyphVector(context, "中文").getOutline().getBounds2D().isEmpty()).isFalse();
            try (var input = new RandomAccessReadBuffer(fontBytes); var font = new TTFParser().parse(input)) {
                assertThat(font.getNumberOfGlyphs()).isLessThan(300);
                assertThat(font.getOS2Windows().getFsType()).isZero();
                assertThat(font.getOS2Windows().getLength()).isGreaterThanOrEqualTo(96);
                assertThat(font.getOS2Windows().getWeightClass()).isEqualTo(style.equals("Bold") ? 700 : 400);
                for (int character : text.codePoints().toArray()) {
                    int glyph = font.getUnicodeCmapLookup().getGlyphId(character);
                    assertThat(glyph).as("%s U+%04X", style, character).isPositive();
                    if (!Character.isSpaceChar(character)) {
                        assertThat(font.getGlyph().getGlyph(glyph)).isNotNull();
                        assertThat(font.getGlyph().getGlyph(glyph).getNumberOfContours()).isNotZero();
                        assertThat(font.getAdvanceWidth(glyph)).isPositive();
                    }
                }
                assertThat(font.getUnicodeCmapLookup().getGlyphId('龘')).isZero();
            }
            int checksum = 0;
            for (int index = 0; index < fontBytes.length; index++) {
                checksum += (fontBytes[index] & 0xff) << (24 - 8 * (index % 4));
            }
            assertThat(checksum).as("OpenType whole-font checksum").isEqualTo(0xB1B0AFBA);
        }
    }

    @Test
    void allocatesNarrativeColumnsAndTopAlignsBodyWithoutPreventingPageSplits() throws Exception {
        String row = "<tr><td>" + "观测记录".repeat(150) + "</td><td>" + "实验结论".repeat(20) + "</td><td>备注</td></tr>";
        String html = "<table><tr><th>记录</th><th>结果</th><th>状态</th></tr>" + row + "</table>";
        String banner = "<tr><td colspan='3'>" + "通栏说明".repeat(200) + "</td></tr>";
        var plain = Jsoup.parse(text(unzip(service.export(html, "table").content()), "word/document.xml"), "", Parser.xmlParser());
        var merged = Jsoup.parse(text(unzip(service.export(html.replace(row, banner + row), "table").content()), "word/document.xml"), "", Parser.xmlParser());
        var widths = plain.getElementsByTag("w:gridCol").stream().mapToInt(e -> Integer.parseInt(e.attr("w:w"))).toArray();
        assertThat(widths).hasSize(3);
        assertThat(widths[0]).isGreaterThan(widths[1]);
        assertThat(widths[1]).isGreaterThan(widths[2]);
        assertThat(java.util.Arrays.stream(widths).sum()).isEqualTo(9360);
        assertThat(java.util.Arrays.stream(widths).allMatch(width -> width >= 720)).isTrue();
        assertThat(merged.getElementsByTag("w:gridCol").stream().mapToInt(e -> Integer.parseInt(e.attr("w:w"))).toArray()).containsExactly(widths);
        var aligns = plain.getElementsByTag("w:vAlign");
        assertThat(aligns.get(0).attr("w:val")).isEqualTo("center");
        assertThat(aligns.get(3).attr("w:val")).isEqualTo("top");
        assertThat(plain.getElementsByTag("w:cantSplit")).isEmpty();
        assertThat(plain.getElementsByTag("w:keepNext")).isEmpty();
    }

    @Test
    void scalesLargeTableImagesToTheMergedCellContentWidth() throws Exception {
        var png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1200, 240, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", png);
        String data = java.util.Base64.getEncoder().encodeToString(png.toByteArray());
        for (int span : new int[]{1, 2}) {
            String html = "<table><tr><td colspan='" + span + "'><img src='data:image/png;base64," + data
                    + "'></td><td>" + "实验记录".repeat(100) + "</td></tr></table>";
            var xml = Jsoup.parse(text(unzip(service.export(html, "image-width").content()), "word/document.xml"), "", Parser.xmlParser());
            var cell = xml.getElementsByTag("w:tc").first();
            long cellWidth = Long.parseLong(cell.getElementsByTag("w:tcW").first().attr("w:w"));
            var extent = cell.getElementsByTag("wp:extent").first();
            long width = Long.parseLong(extent.attr("cx"));
            long height = Long.parseLong(extent.attr("cy"));
            assertThat(width).isLessThanOrEqualTo((cellWidth - 280) * 635);
            assertThat(Math.abs(width - height * 5)).isLessThanOrEqualTo(5);
        }
    }

    @Test
    void exportsSemanticHtmlAsOpenableDocxWithEditableContent() throws Exception {
        String image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
        String html = """
                <div class="document-title">网页预览标题</div>
                <h1 data-docx-title>二次函数课程</h1>
                <h2 class="lesson-heading lesson-heading-center">学习目标</h2>
                <p>普通 <strong>粗体</strong> <em>斜体</em> <u>下划线</u> H<sub>2</sub>O x<sup>2</sup><br>换行</p>
                <ul><li>无序一<ul><li>无序二</li></ul></li></ul>
                <ol><li>步骤一</li></ol>
                <p><span data-latex="x^2+\\frac{1}{2}\\leq\\sqrt{y}"></span></p>
                <p><span data-latex="\\sin x+\\cos x+90^\\circ"></span></p>
                <table><tr><th colspan="2">表头</th></tr><tr><td rowspan="2">甲</td><td>乙</td></tr><tr><td>丙</td></tr></table>
                <figure><img alt="示意图" src="data:image/png;base64,%s"><figcaption>图 1</figcaption></figure>
                """.formatted(image);

        HtmlToDocxService.ExportResult result = service.export(html, "../课程：第一讲");
        Map<String, byte[]> parts = unzip(result.content());
        String document = text(parts, "word/document.xml");

        assertThat(result.filename()).isEqualTo("课程：第一讲.docx");
        assertThat(document).contains("二次函数课程", "学习目标", "粗体", "换行");
        assertThat(document).doesNotContain("网页预览标题");
        assertThat(document).contains("w:pStyle w:val=\"HTMLTitle\"", "w:jc w:val=\"center\"");
        assertThat(document).contains("w:b", "w:i", "w:u", "superscript", "subscript");
        assertThat(document).contains("m:oMath", "m:sSup", "m:f", "m:rad", "≤", "sin", "cos", "∘");
        assertThat(document).contains("w:tbl", "w:gridSpan w:val=\"2\"", "w:vMerge w:val=\"restart\"");
        assertThat(document).contains("w:drawing");
        assertThat(text(parts, "word/numbering.xml")).contains("w:numFmt w:val=\"bullet\"", "w:numFmt w:val=\"decimal\"");
        assertThat(text(parts, "word/styles.xml")).contains("Lesson Heading 1", "Noto Sans SC", "w:eastAsia=\"zh-CN\"");
        assertThat(text(parts, "word/settings.xml")).contains("w:embedTrueTypeFonts", "w:saveSubsetFonts");
        assertThat(text(parts, "word/fontTable.xml")).contains("w:embedRegular", "w:embedBold");
        assertThat(parts).containsKeys("word/fonts/NotoSansSC-regular.odttf", "word/fonts/NotoSansSC-bold.odttf");
        assertThat(parts.keySet()).anyMatch(name -> name.startsWith("word/media/"));
        assertThat(text(parts, "word/_rels/document.xml.rels")).contains("relationships/image");
        assertThat(text(parts, "word/_rels/document.xml.rels")).contains("relationships/fontTable");
        assertThat(text(parts, "[Content_Types].xml")).contains("Extension=\"odttf\"");

        WordprocessingMLPackage loaded = WordprocessingMLPackage.load(new ByteArrayInputStream(result.content()));
        assertThat(loaded.getMainDocumentPart().getContent()).isNotEmpty();
    }

    @Test
    void preservesCaptionAndMixedImageFormulaTextOrderInsideCells() throws Exception {
        String image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
        String html = "<table><caption>实验记录标题</caption><tr><td>观察前<img src='data:image/png;base64,"
                + image + "'>观察后<span data-latex='A_{底}=2'></span>结束</td></tr></table>";
        String xml = text(unzip(service.export(html, "ordered").content()), "word/document.xml");
        assertThat(xml.indexOf("实验记录标题")).isLessThan(xml.indexOf("<w:tbl>"));
        assertThat(xml.indexOf("观察前")).isLessThan(xml.indexOf("<w:drawing>"));
        assertThat(xml.indexOf("<w:drawing>")).isLessThan(xml.indexOf("观察后"));
        assertThat(xml.indexOf("观察后")).isLessThan(xml.indexOf("<m:oMath"));
        assertThat(xml.indexOf("<m:oMath")).isLessThan(xml.indexOf("结束"));
    }

    @Test
    void rejectsUnsupportedFormulasInsteadOfExportingRawLatex() {
        for (String html : new String[]{"<p><span data-latex='\\unknown{x}'></span></p>",
                "<table><tr><td><span data-latex='\\unknown{x}'></span></td></tr></table>"}) {
            assertThatThrownBy(() -> service.export(html, "bad-formula"))
                    .isInstanceOf(AppException.class)
                    .satisfies(error -> assertThat(((AppException) error).code()).isEqualTo("DOCX_FORMULA_UNSUPPORTED"));
        }
    }

    @Test
    void supportsDisablingRepeatedTableHeader() throws Exception {
        byte[] content = service.export("<table data-repeat-header='false'><tr><th>A</th></tr><tr><td>B</td></tr></table>", "a").content();
        assertThat(text(unzip(content), "word/document.xml")).doesNotContain("w:tblHeader");
    }

    @Test
    void embedsDataUrlImagesInsideTableCells() throws Exception {
        String image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
        byte[] content = service.export("""
                <table><tr><td>单元格图片 <img alt="像素" src="data:image/png;base64,%s"></td></tr></table>
                """.formatted(image), "table-image").content();
        Map<String, byte[]> parts = unzip(content);

        assertThat(parts.keySet()).anyMatch(name -> name.startsWith("word/media/"));
        assertThat(text(parts, "word/document.xml")).contains("w:drawing");
        assertThat(text(parts, "word/_rels/document.xml.rels")).contains("relationships/image");
    }

    @Test
    void rejectsHtmlWithoutExportableContent() {
        assertThatThrownBy(() -> service.export("<script>alert(1)</script><div class='document-title'>预览</div>", "x"))
                .isInstanceOf(AppException.class)
                .satisfies(error -> assertThat(((AppException) error).code()).isEqualTo("EMPTY_HTML"));
    }

    @Test
    void rejectsOversizedTableGeometryBeforeExpandingIt() {
        for (String html : new String[]{
                "<table><tr><td colspan='2147483647'>x</td></tr></table>",
                "<table><tr><td rowspan='2147483647'>x</td></tr></table>",
                "<table><tr><td colspan='60'>x</td><td colspan='60'>y</td></tr></table>",
                "<table>" + "<tr><td colspan='100'>x</td></tr>".repeat(101) + "</table>",
                "<table>" + "<tr><td>x</td></tr>".repeat(2001) + "</table>"
        }) {
            assertThatThrownBy(() -> service.export(html, "bounded.docx"))
                    .isInstanceOf(AppException.class)
                    .satisfies(error -> assertThat(((AppException) error).code()).isEqualTo("TABLE_TOO_LARGE"));
        }
    }

    @Test
    void rejectsOverflowingSpanInsteadOfSilentlyTreatingItAsOne() {
        assertThatThrownBy(() -> service.export("<table><tr><td colspan='99999999999999999999999'>x</td></tr></table>", "x"))
                .isInstanceOf(AppException.class)
                .satisfies(error -> assertThat(((AppException) error).code()).isEqualTo("INVALID_TABLE_SPAN"));
    }

    private Map<String, byte[]> unzip(byte[] data) throws Exception {
        Map<String, byte[]> result = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) result.put(entry.getName(), zip.readAllBytes());
            }
        }
        return result;
    }

    private String text(Map<String, byte[]> parts, String name) {
        assertThat(parts).containsKey(name);
        return new String(parts.get(name), StandardCharsets.UTF_8);
    }
}
