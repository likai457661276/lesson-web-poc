package com.lessonweb.lesson.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lessonweb.lesson.model.lesson.ParagraphBlock;
import com.lessonweb.lesson.model.lesson.TableBlock;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MineruReadingOrderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final MineruReadingOrder order = new MineruReadingOrder();

    @Test
    void restoresIndependentColumnsAcrossPageSizesWithoutGuessingFromText() {
        for (int scale : new int[]{1, 3}) {
            TableBlock result = (TableBlock) order.restore(item("甲一乙一甲二乙二甲三乙三"), layout(scale, 60), "block");
            var cells = Jsoup.parse(result.html()).select("td");
            assertThat(cells).hasSize(2);
            assertThat(cells.get(0).text()).isEqualTo("甲一 甲二 甲三");
            assertThat(cells.get(1).text()).isEqualTo("乙一 乙二 乙三");
        }
    }

    @Test
    void flagsAmbiguousTextWithoutReplacingOrDroppingTheOriginal() {
        ParagraphBlock result = (ParagraphBlock) order.restore(item("原始文字与坐标结果不一致"), layout(1, 60), "block");
        assertThat(result.text()).isEqualTo("原始文字与坐标结果不一致");
        assertThat(result.reviewNote()).contains("第 1 页", "复核阅读顺序");
    }

    @Test
    void leavesOrdinaryWordSpacingAndMissingLayoutUnchanged() {
        assertThat(order.restore(item("甲一乙一甲二乙二甲三乙三"), layout(1, 43), "block")).isNull();
        assertThat(order.restore(item("正文"), mapper.createObjectNode(), "block")).isNull();
    }

    @Test
    void normalizedCoordinatesRestoreTheSameColumns() {
        ObjectNode normalized = item("甲一乙一甲二乙二甲三乙三");
        normalized.putArray("bbox").add(0.05).add(0.05).add(0.95).add(0.3);
        assertThat(order.restore(normalized, layout(3, 60), "block"))
                .isEqualTo(order.restore(item("甲一乙一甲二乙二甲三乙三"), layout(3, 60), "block"));
    }

    @Test
    void retainsUnequalColumnsWithAnActionableReviewNote() {
        ObjectNode irregular = layout(1, 60);
        ArrayNode spans = (ArrayNode) irregular.path("pdf_info").get(0).path("preproc_blocks")
                .get(0).path("lines").get(2).path("spans");
        spans.remove(1);
        ParagraphBlock result = (ParagraphBlock) order.restore(item("甲一乙一甲二乙二甲三"), irregular, "block");
        assertThat(result.text()).isEqualTo("甲一乙一甲二乙二甲三");
        assertThat(result.reviewNote()).contains("分段不规则", "复核阅读顺序");
    }

    @Test
    void flagsShortColumnsWithoutInventingMissingRows() {
        ObjectNode shortColumns = layout(1, 60);
        ((ArrayNode) shortColumns.path("pdf_info").get(0).path("preproc_blocks").get(0).path("lines")).remove(2);
        ParagraphBlock result = (ParagraphBlock) order.restore(item("甲一乙一甲二乙二"), shortColumns, "block");
        assertThat(result.text()).isEqualTo("甲一乙一甲二乙二");
        assertThat(result.reviewNote()).contains("复核阅读顺序");
    }

    @Test
    void keepsMixedFormulaContentForReviewRatherThanDroppingIt() {
        ObjectNode mixed = layout(1, 60);
        ((ObjectNode) mixed.path("pdf_info").get(0).path("preproc_blocks").get(0)
                .path("lines").get(0).path("spans").get(1)).put("type", "inline_equation");
        ParagraphBlock result = (ParagraphBlock) order.restore(item("甲一乙一甲二乙二甲三乙三"), mixed, "block");
        assertThat(result.text()).isEqualTo("甲一乙一甲二乙二甲三乙三");
        assertThat(result.reviewNote()).contains("文字与版面信息不一致");
    }

    private ObjectNode item(String text) {
        ObjectNode item = mapper.createObjectNode().put("type", "text").put("text", text).put("page_idx", 0);
        item.putArray("bbox").add(50).add(50).add(950).add(300);
        return item;
    }

    private ObjectNode layout(int scale, int rightStart) {
        ObjectNode layout = mapper.createObjectNode();
        ObjectNode page = layout.putArray("pdf_info").addObject().put("page_idx", 0);
        page.putArray("page_size").add(200 * scale).add(400 * scale);
        ObjectNode block = page.putArray("preproc_blocks").addObject().put("type", "text");
        block.putArray("bbox").add(10 * scale).add(20 * scale).add(190 * scale).add(120 * scale);
        ArrayNode lines = block.putArray("lines");
        String[] labels = {"一", "二", "三"};
        for (int row = 0; row < 3; row++) {
            ArrayNode spans = lines.addObject().putArray("spans");
            for (int column = 0; column < 2; column++) {
                ObjectNode span = spans.addObject().put("type", "text").put("content", (column == 0 ? "甲" : "乙") + labels[row]);
                span.putArray("bbox").add((column == 0 ? 15 : rightStart) * scale).add((30 + row * 25) * scale)
                        .add((column == 0 ? 40 : 180) * scale).add((40 + row * 25) * scale);
            }
        }
        return layout;
    }
}
