package com.lessonweb.lesson.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lessonweb.lesson.model.lesson.FormulaBlock;
import com.lessonweb.lesson.model.lesson.HeadingBlock;
import com.lessonweb.lesson.model.lesson.ImageBlock;
import com.lessonweb.lesson.model.lesson.LessonDocument;
import com.lessonweb.lesson.model.lesson.TableBlock;
import com.lessonweb.lesson.model.lesson.TextAlignment;
import com.lessonweb.lesson.model.lesson.ParagraphBlock;
import com.lessonweb.lesson.exception.AppException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lessonweb.lesson.parser.MineruParseResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void preservesProviderBlockBoundariesEvenWhenAlignedWithTable() throws Exception {
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

        assertThat(document.blocks()).extracting(block -> block.type())
                .containsExactly("heading", "heading", "paragraph", "heading", "paragraph", "table");
        assertThat(document.title()).isEqualTo("第一课时");
        String html = ((TableBlock) document.blocks().get(5)).html();
        assertThat(html).contains("data-latex=\"b &lt; 720\"")
                .doesNotContain("lesson-layout", "一、内容", "任意角的概念");
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

    @ParameterizedTest
    @ValueSource(doubles = {1, 0.001})
    void preservesIndependentNarrativeAcrossCoordinateUnits(double scale) throws Exception {
        JsonNode content = objectMapper.readTree("""
                [
                  {"type":"text","text_level":1,"text":"Observations","page_idx":4,"bbox":[100,20,400,45]},
                  {"type":"text","text":"Independent introduction.","page_idx":4,"bbox":[100,400,800,420]},
                  {"type":"text","text":"Measurements follow.","page_idx":4,"bbox":[100,440,800,460]},
                  {"type":"table","page_idx":4,"bbox":[100,480,900,900],
                   "table_body":"<table><tr><td>Sample</td><td>Value</td></tr></table>"}
                ]
                """);
        scaleBoxes(content, scale);
        var document = adapter.convert(result(content, objectMapper.createArrayNode()), "unseen", "report.pdf", Map.of());
        assertThat(document.title()).isEqualTo("Observations");
        assertThat(document.blocks()).extracting(block -> block.type())
                .containsExactly("heading", "paragraph", "paragraph", "table");
        assertThat(((HeadingBlock) document.blocks().get(0)).level()).isEqualTo(1);
        assertThat(((ParagraphBlock) document.blocks().get(1)).text()).isEqualTo("Independent introduction.");
        assertThat(((TableBlock) document.blocks().get(3)).html()).doesNotContain("Observations", "introduction");
    }

    @ParameterizedTest
    @ValueSource(doubles = {1, 0.001})
    void headingAlignmentIsIndependentOfCoordinateUnits(double scale) throws Exception {
        JsonNode content = objectMapper.readTree("""
                [{"type":"heading","text":"Appendix","bbox":[420,80,580,110]},
                 {"type":"heading","text":"Notes","bbox":[760,130,920,160]}]
                """);
        scaleBoxes(content, scale);
        var document = adapter.convert(result(content, objectMapper.createArrayNode()), "geometry", "report.pdf", Map.of());
        assertThat(((HeadingBlock) document.blocks().get(0)).alignment()).isEqualTo(TextAlignment.CENTER);
        assertThat(((HeadingBlock) document.blocks().get(1)).alignment()).isEqualTo(TextAlignment.RIGHT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "null", "{}"})
    void rejectsEmptyOrInvalidContentList(String json) throws Exception {
        JsonNode content = objectMapper.readTree(json);
        assertThatThrownBy(() -> adapter.convert(result(content, objectMapper.createArrayNode()), "empty", "blank.pdf", Map.of()))
                .isInstanceOf(AppException.class)
                .satisfies(error -> assertThat(((AppException) error).code()).isEqualTo("DOCUMENT_CONTENT_EMPTY"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "null",
            "{\"type\":\"image\",\"img_path\":\"images/missing.png\"}",
            "{\"type\":\"table\",\"img_path\":\"images/table.png\"}",
            "{\"type\":\"table\",\"table_body\":\"<table></table>\"}",
            "{\"type\":\"table\",\"table_body\":\"<table><tr><td><img src=missing.png></td></tr></table>\"}",
            "{\"type\":\"list\",\"items\":[\"retained\",{}]}",
            "{\"type\":\"list\",\"items\":[{\"text\":\"Parent\",\"list_items\":[\"Child\"]}]}",
            "{\"type\":\"unrecognized\",\"text\":\"Parent\",\"children\":[{\"text\":\"Child\"}]}",
            "{\"type\":\"formula\",\"latex\":\"\"}",
            "{\"type\":\"heading\",\"text\":\"\"}",
            "{\"type\":\"unrecognized\"}"
    })
    void rejectsUnrepresentableBlocksInsteadOfSilentlyDroppingThem(String item) throws Exception {
        JsonNode content = objectMapper.readTree("[{\"type\":\"text\",\"text\":\"Useful content\"}," + item + "]");
        assertThatThrownBy(() -> adapter.convert(result(content, objectMapper.createArrayNode()), "partial", "report.pdf", Map.of()))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("第 2 个内容块")
                .satisfies(error -> {
                    assertThat(((AppException) error).code()).isEqualTo("DOCUMENT_CONTENT_INCOMPLETE");
                    assertThat(((AppException) error).status().value()).isEqualTo(422);
                });
    }

    @Test
    void retainsUnknownTextWithReviewNoteAndSourceLocation() throws Exception {
        JsonNode content = objectMapper.readTree("""
                [{"type":"unrecognized","text":"Independent source text","page_idx":6}]
                """);
        var document = adapter.convert(result(content, objectMapper.createArrayNode()), "unknown", "report.pdf", Map.of());
        ParagraphBlock block = (ParagraphBlock) document.blocks().get(0);
        assertThat(block.text()).isEqualTo("Independent source text");
        assertThat(block.reviewNote()).contains("第 7 页", "第 1 个内容块", "未识别内容类型");
    }

    @Test
    void rewritesUnquotedTableImagesWithoutChangingFormulaMarkup() throws Exception {
        JsonNode content = objectMapper.readTree("""
                [{"type":"table","table_body":"<table><tr><td><img src=images/plot.png><eq>x^2</eq></td></tr></table>"}]
                """);
        var document = adapter.convert(result(content, objectMapper.createArrayNode()), "assets", "report.pdf",
                Map.of("images/plot.png", "/api/assets/assets/plot.png"));
        assertThat(((TableBlock) document.blocks().get(0)).html())
                .contains("src=\"/api/assets/assets/plot.png\"", "data-latex=\"x^2\"");
    }

    @ParameterizedTest
    @ValueSource(doubles = {1, 0.001})
    void representsExplicitlyDeletedCrossPageFragmentsAsReviewNotes(double scale) throws Exception {
        JsonNode content = objectMapper.readTree("""
                [{"type":"table","table_body":"<table><tr><td>Combined observations</td></tr></table>"},
                 {"type":"table","img_path":"","table_caption":[],"table_footnote":[],"page_idx":8,"bbox":[100,200,900,800]}]
                """);
        scaleBoxes(objectMapper.createArrayNode().add(content.get(1)), scale);
        var document = adapter.convert(new MineruParseResult(content, objectMapper.createArrayNode(), deletedTableLayout(), Path.of("unused")),
                "continuation", "independent.pdf", Map.of());
        assertThat(document.blocks()).extracting(block -> block.type()).containsExactly("table", "paragraph");
        assertThat(((TableBlock) document.blocks().get(0)).html()).contains("Combined observations");
        ParagraphBlock review = (ParagraphBlock) document.blocks().get(1);
        assertThat(review.text()).isEmpty();
        assertThat(review.reviewNote()).contains("第 9 页", "跨页内容是否完整");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-marker", "different-page", "different-box", "ambiguous-box", "nonempty-lines"})
    void doesNotGuessThatAnEmptyTableWasMergedElsewhere(String scenario) throws Exception {
        ObjectNode layout = deletedTableLayout();
        ObjectNode page = (ObjectNode) layout.path("pdf_info").get(0);
        ArrayNode tables = (ArrayNode) page.path("para_blocks");
        ObjectNode table = (ObjectNode) tables.get(0);
        ObjectNode body = (ObjectNode) table.path("blocks").get(0);
        switch (scenario) {
            case "missing-marker" -> body.remove("lines_deleted");
            case "different-page" -> page.put("page_idx", 7);
            case "different-box" -> table.putArray("bbox").add(1).add(1).add(20).add(30);
            case "ambiguous-box" -> tables.add(table.deepCopy());
            default -> ((ArrayNode) body.path("lines")).addObject().put("text", "Source content");
        }
        JsonNode content = objectMapper.readTree("""
                [{"type":"table","page_idx":8,"bbox":[100,200,900,800]}]
                """);
        assertThatThrownBy(() -> adapter.convert(new MineruParseResult(content, objectMapper.createArrayNode(), layout, Path.of("unused")),
                "not-merged", "independent.pdf", Map.of()))
                .isInstanceOf(AppException.class)
                .satisfies(error -> assertThat(((AppException) error).code()).isEqualTo("DOCUMENT_CONTENT_INCOMPLETE"));
    }

    @Test
    void reviewOnlyResultCannotBeReportedAsUsableDocument() throws Exception {
        JsonNode content = objectMapper.readTree("""
                [{"type":"table","page_idx":8,"bbox":[100,200,900,800]}]
                """);
        assertThatThrownBy(() -> adapter.convert(new MineruParseResult(content, objectMapper.createArrayNode(), deletedTableLayout(), Path.of("unused")),
                "no-content", "independent.pdf", Map.of()))
                .isInstanceOf(AppException.class)
                .satisfies(error -> assertThat(((AppException) error).code()).isEqualTo("DOCUMENT_CONTENT_EMPTY"));
    }

    private ObjectNode deletedTableLayout() throws Exception {
        return (ObjectNode) objectMapper.readTree("""
                {"pdf_info":[{"page_idx":8,"page_size":[300,600],"para_blocks":[
                  {"type":"table","bbox":[30,120,270,480],"blocks":[
                    {"type":"table_body","lines_deleted":true,"lines":[]}
                  ]}
                ]}]}
                """);
    }

    private void scaleBoxes(JsonNode content, double scale) {
        content.forEach(item -> {
            ArrayNode box = (ArrayNode) item.path("bbox");
            for (int i = 0; i < box.size(); i++) box.set(i, DoubleNode.valueOf(box.get(i).asDouble() * scale));
        });
    }

    private MineruParseResult result(JsonNode content, JsonNode ocr) {
        return new MineruParseResult(content, ocr, objectMapper.createObjectNode(), Path.of("mineru"));
    }
}
