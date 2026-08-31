package com.lessonweb.lesson.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.lessonweb.lesson.model.lesson.DocumentBlock;
import com.lessonweb.lesson.model.lesson.ParagraphBlock;
import com.lessonweb.lesson.model.lesson.TableBlock;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Restores columns only when repeated span geometry and the complete text agree. */
final class MineruReadingOrder {
    DocumentBlock restore(JsonNode item, JsonNode layout, String id) {
        if (layout == null || !item.has("page_idx")) return null;
        int pageIndex = item.path("page_idx").asInt(-1);
        Box target = box(item.path("bbox"));
        if (target == null) return null;
        double scale = target.right <= 1.5 && target.bottom <= 1.5 ? 1 : 1000;
        target = target.scale(scale, scale);
        for (JsonNode page : layout.path("pdf_info")) {
            if (page.path("page_idx").asInt(-1) != pageIndex) continue;
            double width = page.path("page_size").path(0).asDouble();
            double height = page.path("page_size").path(1).asDouble();
            if (width <= 0 || height <= 0) return null;
            List<JsonNode> matches = new ArrayList<>();
            for (JsonNode block : page.path("preproc_blocks")) {
                if (!"text".equals(block.path("type").asText())) continue;
                Box bounds = box(block.path("bbox"));
                if (bounds != null && target.overlap(bounds.scale(width, height)) >= 0.85) matches.add(block);
            }
            if (matches.size() != 1) return null;
            return restoreColumns(item, matches.get(0), id, pageIndex + 1);
        }
        return null;
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
            spans.sort(Comparator.comparingDouble(s -> s.box.left));
            rows.add(spans);
        }
        if (rows.size() < 3) return null;
        int columns = rows.get(0).size();
        if (columns < 2 || columns > 4 || rows.stream().anyMatch(row -> row.size() != columns)) return null;
        rows.sort(Comparator.comparingDouble(row -> row.get(0).box.top));
        double lineHeight = rows.stream().flatMap(List::stream)
                .mapToDouble(span -> span.box.bottom - span.box.top).average().orElse(0);
        // A gutter must remain open across every line; ordinary word spacing does not qualify.
        for (int column = 1; column < columns; column++) {
            double leftEdge = Double.POSITIVE_INFINITY, rightEdge = Double.NEGATIVE_INFINITY;
            for (List<Span> row : rows) {
                leftEdge = Math.min(leftEdge, row.get(column).box.left);
                rightEdge = Math.max(rightEdge, row.get(column - 1).box.right);
            }
            if (leftEdge - rightEdge < lineHeight) return null;
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
            return new ParagraphBlock(id, original,
                    "第 " + page + " 页存在多栏内容，文字与版面信息不一致；请对照原文复核阅读顺序。");
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

    private Box box(JsonNode values) {
        if (!values.isArray() || values.size() != 4) return null;
        for (JsonNode value : values) if (!value.isNumber() || !Double.isFinite(value.asDouble())) return null;
        Box result = new Box(values.get(0).asDouble(), values.get(1).asDouble(),
                values.get(2).asDouble(), values.get(3).asDouble());
        return result.right > result.left && result.bottom > result.top ? result : null;
    }

    private record Span(Box box, String text, String type) {}
    private record Box(double left, double top, double right, double bottom) {
        Box scale(double width, double height) { return new Box(left / width, top / height, right / width, bottom / height); }
        double overlap(Box other) {
            double intersection = Math.max(0, Math.min(right, other.right) - Math.max(left, other.left))
                    * Math.max(0, Math.min(bottom, other.bottom) - Math.max(top, other.top));
            return intersection / ((right - left) * (bottom - top)
                    + (other.right - other.left) * (other.bottom - other.top) - intersection);
        }
    }
}
