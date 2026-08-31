package com.lessonweb.lesson.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lessonweb.lesson.model.lesson.FormulaBlock;
import com.lessonweb.lesson.model.lesson.HeadingBlock;
import com.lessonweb.lesson.model.lesson.ImageBlock;
import com.lessonweb.lesson.model.lesson.LessonDocument;
import com.lessonweb.lesson.model.lesson.TableBlock;
import com.lessonweb.lesson.model.lesson.TextAlignment;
import com.lessonweb.lesson.parser.MineruParseResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MineruLessonDocumentAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MineruLessonDocumentAdapter adapter = new MineruLessonDocumentAdapter();

    @Test
    void mapsAllSixBlockTypesAndRewritesEmbeddedReferences() throws Exception {
        JsonNode content = objectMapper.readTree("""
                [
                  {"type":"text","text_level":1,"text":"勾股定理"},
                  {"type":"text","text":"理解定理。"},
                  {"type":"list","items":["观察","证明"]},
                  {"type":"table","table_body":"<table><tr><td><img src='images/a.png'>$-360^{\\\\circ} \\\\leq b$</td></tr></table>"},
                  {"type":"image","img_path":"images/a.png","image_caption":["示意图"]},
                  {"type":"interline_equation","text":"a^2+b^2=c^2"}
                ]
                """);
        LessonDocument document = adapter.convert(
                result(content, objectMapper.createArrayNode()),
                "job-1",
                "lesson.pdf",
                Map.of("images/a.png", "/api/assets/job-1/a.png"));

        assertThat(document.title()).isEqualTo("勾股定理");
        assertThat(document.blocks()).extracting(block -> block.type())
                .containsExactly("heading", "paragraph", "list", "table", "image", "formula");
        assertThat(((ImageBlock) document.blocks().get(4)).src()).isEqualTo("/api/assets/job-1/a.png");
        String html = ((TableBlock) document.blocks().get(3)).html();
        assertThat(html).contains("/api/assets/job-1/a.png", "data-latex=\"-360^{\\circ} \\leq b\"");
    }

    @Test
    void retainsTableCaptionsBeforeTheTableBody() throws Exception {
        JsonNode content = objectMapper.readTree("""
                [{"type":"table","table_caption":["实验 A & B","结果汇总"],
                  "table_body":"<table><tr><td>测量值</td></tr></table>"}]
                """);
        LessonDocument document = adapter.convert(result(content, objectMapper.createArrayNode()),
                "caption", "arbitrary.pdf", Map.of());
        String html = ((TableBlock) document.blocks().get(0)).html();
        assertThat(html).contains("<caption>实验 A &amp; B\n结果汇总</caption>");
        assertThat(html.indexOf("<caption>")).isLessThan(html.indexOf("<tr>"));
    }

    @Test
    void stripsHeadingMarkupNormalizesDegreesAndInfersAlignment() throws Exception {
        JsonNode content = objectMapper.readTree("""
                [
                  {"type":"title","text":"第一<sub>课时</sub>","bbox":[430,80,570,112]},
                  {"type":"formula","latex":"30°"},
                  {"type":"text","text_level":2,"text":"右侧标题","bbox":[735,220,880,245]}
                ]
                """);
        LessonDocument document = adapter.convert(result(content, objectMapper.createArrayNode()),
                "job-2", "lesson.pdf", Map.of());

        assertThat(document.title()).isEqualTo("第一课时");
        assertThat(((HeadingBlock) document.blocks().get(0)).alignment()).isEqualTo(TextAlignment.CENTER);
        assertThat(((FormulaBlock) document.blocks().get(1)).latex()).isEqualTo("30^{\\circ}");
        assertThat(((HeadingBlock) document.blocks().get(2)).alignment()).isEqualTo(TextAlignment.RIGHT);
    }

    @Test
    void restoresHeadingSpacingOnlyForUnambiguousOcrRuns() throws Exception {
        JsonNode content = objectMapper.readTree("""
                [{"type":"text","text_level":2,"text":"2.4 通用章节","page_idx":0,"bbox":[390,130,610,160]}]
                """);
        JsonNode ocr = objectMapper.readTree("""
                [[{"bbox":[0.39,0.13,0.46,0.16]},{"bbox":[0.51,0.13,0.61,0.16]}]]
                """);
        LessonDocument document = adapter.convert(result(content, ocr),
                "job-spacing", "arbitrary.pdf", Map.of());

        assertThat(((HeadingBlock) document.blocks().get(0)).text()).isEqualTo("2.4　　通用章节");
    }

    @Test
    void mergesGeometryMatchedPrefixRowsIntoLayoutTable() throws Exception {
        JsonNode content = objectMapper.readTree("""
                [
                  {"type":"text","text_level":1,"text":"第一课时","page_idx":0,"bbox":[430,80,570,115]},
                  {"type":"text","text_level":2,"text":"一、内容","page_idx":0,"bbox":[144,170,240,195]},
                  {"type":"text","text":"任意角的概念","page_idx":0,"bbox":[142,205,550,230]},
                  {"type":"text","text_level":2,"text":"二、教学策略","page_idx":0,"bbox":[144,240,280,263]},
                  {"type":"text","text":"形成问题链","page_idx":0,"bbox":[178,268,850,291]},
                  {"type":"table","page_idx":0,"bbox":[147,290,875,900],"table_body":"<table><tr><td>核心问题</td><td>主干问题</td><td colspan='2'>$b &lt; 720$</td></tr></table>"}
                ]
                """);
        LessonDocument document = adapter.convert(result(content, objectMapper.createArrayNode()),
                "job-layout", "lesson.pdf", Map.of());

        assertThat(document.blocks()).extracting(block -> block.type()).containsExactly("heading", "table");
        String html = ((TableBlock) document.blocks().get(1)).html();
        assertThat(html).contains("lesson-layout-table", "data-repeat-header=\"false\"",
                "lesson-layout-heading-cell", "lesson-layout-centered-cell", "data-latex=\"b &lt; 720\"");
        assertThat(html.indexOf("一、内容")).isLessThan(html.indexOf("任意角的概念"));
        assertThat(html.indexOf("任意角的概念")).isLessThan(html.indexOf("核心问题"));
    }

    @Test
    void keepsUnalignedParagraphsOutsideRegularTable() throws Exception {
        JsonNode content = objectMapper.readTree("""
                [
                  {"type":"text","text_level":2,"text":"数据分析","page_idx":0,"bbox":[100,100,300,125]},
                  {"type":"text","text":"下面是统计结果。","page_idx":0,"bbox":[100,140,420,165]},
                  {"type":"table","page_idx":0,"bbox":[300,300,700,500],"table_body":"<table><tr><td>A</td></tr></table>"}
                ]
                """);
        LessonDocument document = adapter.convert(result(content, objectMapper.createArrayNode()),
                "job-regular", "lesson.pdf", Map.of());

        assertThat(document.blocks()).extracting(block -> block.type())
                .containsExactly("heading", "paragraph", "table");
    }

    private MineruParseResult result(JsonNode content, JsonNode ocr) {
        return new MineruParseResult(content, ocr, objectMapper.createObjectNode(), Path.of("mineru"));
    }
}
