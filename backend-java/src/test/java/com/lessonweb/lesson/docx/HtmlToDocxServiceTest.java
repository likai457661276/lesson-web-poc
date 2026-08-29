package com.lessonweb.lesson.docx;

import com.lessonweb.lesson.docx.formula.LatexToMathmlConverter;
import com.lessonweb.lesson.docx.formula.MathmlToOmmlConverter;
import com.lessonweb.lesson.exception.AppException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
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
                <p><span data-latex="\\unknown{x}"></span></p>
                <table><tr><th colspan="2">表头</th></tr><tr><td rowspan="2">甲</td><td>乙</td></tr><tr><td>丙</td></tr></table>
                <figure><img alt="示意图" src="data:image/png;base64,%s"><figcaption>图 1</figcaption></figure>
                """.formatted(image);

        HtmlToDocxService.ExportResult result = service.export(html, "../课程：第一讲");
        Map<String, byte[]> parts = unzip(result.content());
        String document = text(parts, "word/document.xml");

        assertThat(result.filename()).isEqualTo("课程：第一讲.docx");
        assertThat(document).contains("二次函数课程", "学习目标", "粗体", "换行", "\\unknown{x}");
        assertThat(document).doesNotContain("网页预览标题");
        assertThat(document).contains("w:pStyle w:val=\"HTMLTitle\"", "w:jc w:val=\"center\"");
        assertThat(document).contains("w:b", "w:i", "w:u", "superscript", "subscript");
        assertThat(document).contains("m:oMath", "m:sSup", "m:f", "m:rad", "≤", "sin", "cos", "°");
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
    void supportsDisablingRepeatedTableHeader() throws Exception {
        byte[] content = service.export("<table data-repeat-header='false'><tr><th>A</th></tr><tr><td>B</td></tr></table>", "a").content();
        assertThat(text(unzip(content), "word/document.xml")).doesNotContain("w:tblHeader");
    }

    @Test
    void rejectsHtmlWithoutExportableContent() {
        assertThatThrownBy(() -> service.export("<script>alert(1)</script><div class='document-title'>预览</div>", "x"))
                .isInstanceOf(AppException.class)
                .satisfies(error -> assertThat(((AppException) error).code()).isEqualTo("EMPTY_HTML"));
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
