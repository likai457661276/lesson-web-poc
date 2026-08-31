package com.lessonweb.lesson.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.lessonweb.lesson.model.lesson.DocumentBlock;
import com.lessonweb.lesson.adapter.MineruGeometry.Box;
import com.lessonweb.lesson.model.lesson.ParagraphBlock;
import com.lessonweb.lesson.model.lesson.TableBlock;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.lessonweb.lesson.adapter.MineruGeometry.box;

/** Restores columns only when repeated span geometry and the complete text agree. */
final class MineruReadingOrder {
    DocumentBlock restore(JsonNode item, JsonNode layout, String id) {
        JsonNode block = MineruGeometry.matchingBlock(item, layout, "preproc_blocks", "text");
        return block == null ? null : restoreColumns(item, block, id, item.path("page_idx").asInt() + 1);
    }

    private DocumentBlock restoreColumns(JsonNode item, JsonNode block, String id, int page) {
        List<List<Span>> rows = new ArrayList<>();
        for (JsonNode line : block.path("lines")) {
            List<Span> spans = new ArrayList<>();
            for (JsonNode raw : line.path("spans")) {
                Box bounds = box(raw.path("bbox"));
                if (bounds == null) return null;
                spans.add(new Span(bounds, raw.path("content").asText(""), raw.path("type").asText("")));
            }
            if (spans.isEmpty()) return null;
            spans.sort(Comparator.comparingDouble(s -> s.box.left()));
            rows.add(spans);
        }
        int columns = rows.stream().mapToInt(List::size).max().orElse(0);
        if (columns < 2 || columns > 4) return null;
        rows.sort(Comparator.comparingDouble(row -> row.get(0).box.top()));
        double lineHeight = rows.stream().flatMap(List::stream)
                .mapToDouble(span -> span.box.bottom() - span.box.top()).average().orElse(0);
        List<List<Span>> completeRows = rows.stream().filter(row -> row.size() == columns).toList();
        // Repeated gutters are evidence of columns, but do not prove how incomplete rows align.
        if (completeRows.size() < 2) return null;
        for (int column = 1; column < columns; column++) {
            double leftEdge = Double.POSITIVE_INFINITY, rightEdge = Double.NEGATIVE_INFINITY;
            for (List<Span> row : completeRows) {
                leftEdge = Math.min(leftEdge, row.get(column).box.left());
                rightEdge = Math.max(rightEdge, row.get(column - 1).box.right());
            }
            if (leftEdge - rightEdge < lineHeight) return null;
        }
        if (rows.size() < 3 || completeRows.size() != rows.size()) {
            return review(item, id, page, "栏内行数或文字分段不规则");
        }
        String original = item.path("text").asText(item.path("content").asText(""));
        StringBuilder rowOrder = new StringBuilder();
        boolean complete = true;
        for (List<Span> row : rows) {
            for (Span span : row) {
                complete &= "text".equals(span.type) && !span.text.isBlank();
                rowOrder.append(span.text);
            }
        }
        if (!complete || !compact(original).equals(compact(rowOrder.toString()))) {
            return review(item, id, page, "文字与版面信息不一致");
        }
        Element table = new Element(Tag.valueOf("table"), "");
        table.attr("data-repeat-header", "false");
        Element row = table.appendElement("tbody").appendElement("tr");
        for (int column = 0; column < columns; column++) {
            List<String> text = new ArrayList<>();
            for (List<Span> line : rows) text.add(line.get(column).text);
            row.appendElement("td").text(String.join("\n", text));
        }
        return new TableBlock(id, table.outerHtml());
    }

    private String compact(String text) { return text.replaceAll("[\\s\\p{Z}]+", ""); }

    private ParagraphBlock review(JsonNode item, String id, int page, String reason) {
        return new ParagraphBlock(id, item.path("text").asText(item.path("content").asText("")),
                "第 " + page + " 页存在疑似多栏内容，" + reason + "；已保留原文，请对照原文复核阅读顺序。");
    }

    private record Span(Box box, String text, String type) {}
}
