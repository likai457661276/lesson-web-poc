package com.lessonweb.lesson.adapter;

import com.fasterxml.jackson.databind.JsonNode;

/** Provider coordinates stay here: content/model boxes use unit or thousandth-page units,
 * while layout.json boxes use the dimensions in page_size. */
final class MineruGeometry {
    private MineruGeometry() {}

    static Box normalizedBox(JsonNode item) {
        Box box = box(item.path("bbox"));
        if (box == null) return null;
        double scale = box.right() <= 1 && box.bottom() <= 1 ? 1 : 1000;
        Box normalized = box.scale(scale, scale);
        return normalized.right() <= 1 && normalized.bottom() <= 1 ? normalized : null;
    }

    static Box box(JsonNode values) {
        if (!values.isArray() || values.size() != 4) return null;
        for (JsonNode value : values) {
            if (!value.isNumber() || !Double.isFinite(value.asDouble())) return null;
        }
        Box box = new Box(values.get(0).asDouble(), values.get(1).asDouble(),
                values.get(2).asDouble(), values.get(3).asDouble());
        return box.left() >= 0 && box.top() >= 0
                && box.right() > box.left() && box.bottom() > box.top() ? box : null;
    }

    static JsonNode matchingBlock(JsonNode item, JsonNode layout, String collection, String type) {
        if (layout == null || !item.path("page_idx").isIntegralNumber()) return null;
        Box target = normalizedBox(item);
        if (target == null) return null;
        JsonNode match = null;
        for (JsonNode page : layout.path("pdf_info")) {
            if (!page.path("page_idx").equals(item.path("page_idx"))) continue;
            double width = page.path("page_size").path(0).asDouble();
            double height = page.path("page_size").path(1).asDouble();
            if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0) return null;
            for (JsonNode block : page.path(collection)) {
                if (!type.equals(block.path("type").asText())) continue;
                Box bounds = box(block.path("bbox"));
                if (bounds == null || bounds.right() > width || bounds.bottom() > height) continue;
                if (target.overlap(bounds.scale(width, height)) >= 0.85) {
                    if (match != null) return null;
                    match = block;
                }
            }
        }
        return match;
    }

    record Box(double left, double top, double right, double bottom) {
        Box scale(double width, double height) {
            return new Box(left / width, top / height, right / width, bottom / height);
        }

        double overlap(Box other) {
            double intersection = Math.max(0, Math.min(right, other.right) - Math.max(left, other.left))
                    * Math.max(0, Math.min(bottom, other.bottom) - Math.max(top, other.top));
            return intersection / ((right - left) * (bottom - top)
                    + (other.right - other.left) * (other.bottom - other.top) - intersection);
        }
    }
}
