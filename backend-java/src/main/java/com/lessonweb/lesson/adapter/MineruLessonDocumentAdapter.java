package com.lessonweb.lesson.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lessonweb.lesson.model.lesson.DocumentBlock;
import com.lessonweb.lesson.model.lesson.FormulaBlock;
import com.lessonweb.lesson.model.lesson.HeadingBlock;
import com.lessonweb.lesson.model.lesson.ImageBlock;
import com.lessonweb.lesson.model.lesson.LessonDocument;
import com.lessonweb.lesson.model.lesson.LessonMetadata;
import com.lessonweb.lesson.model.lesson.ListBlock;
import com.lessonweb.lesson.model.lesson.ParagraphBlock;
import com.lessonweb.lesson.model.lesson.TableBlock;
import com.lessonweb.lesson.model.lesson.TextAlignment;
import com.lessonweb.lesson.parser.MineruParseResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.parser.Tag;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MineruLessonDocumentAdapter {

    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern LIST_PREFIX = Pattern.compile("^(?:[-*•]|\\d+[.)])\\s+");
    private static final Pattern ORDERED_LIST_PREFIX = Pattern.compile("^\\d+[.)]\\s+");
    private static final Pattern TABLE_IMAGE = Pattern.compile(
            "(<img\\b[^>]*?\\bsrc\\s*=\\s*[\"'])([^\"']+)([\"'])", Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_FORMULA = Pattern.compile(
            "<eq>(.*?)</eq>|(?<!\\\\)\\$(.+?)(?<!\\\\)\\$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public LessonDocument convert(
            MineruParseResult result,
            String documentId,
            String sourceFilename,
            Map<String, String> assetUrls
    ) {
        List<DocumentBlock> blocks = new ArrayList<>();
        String defaultTitle = stem(sourceFilename);
        String title = defaultTitle;
        List<ObjectNode> content = mergeLayoutTablePrefixes(result.contentList());
        for (int index = 0; index < content.size(); index++) {
            DocumentBlock block = convertItem(
                    content.get(index), index + 1, assetUrls, result.ocrLayout());
            if (block == null) {
                continue;
            }
            blocks.add(block);
            if (block instanceof HeadingBlock heading && heading.level() == 1 && title.equals(defaultTitle)) {
                title = heading.text();
            }
        }
        return new LessonDocument(
                "1.0",
                documentId,
                title,
                new LessonMetadata(extension(sourceFilename), sourceFilename),
                blocks
        );
    }

    private DocumentBlock convertItem(
            ObjectNode item,
            int index,
            Map<String, String> assetUrls,
            JsonNode ocrLayout
    ) {
        String itemType = item.path("type").asText("").toLowerCase(Locale.ROOT);
        String blockId = String.format("block-%04d", index);
        String text = text(item);

        if (itemType.equals("title") || itemType.equals("heading") || truthy(item.get("text_level"))) {
            if (text.isEmpty()) {
                return null;
            }
            int level = positiveOrDefault(item.get("text_level"), positiveOrDefault(item.get("level"), 1));
            level = Math.max(1, Math.min(6, level));
            return new HeadingBlock(
                    blockId,
                    level,
                    restoreHeadingSpacing(plainText(text), item, ocrLayout),
                    headingAlignment(item)
            );
        }

        if (itemType.equals("list") || itemType.equals("list_item")) {
            JsonNode rawItems = firstPresent(item, "items", "list_items");
            List<String> items = new ArrayList<>();
            if (rawItems != null && rawItems.isArray()) {
                for (JsonNode rawItem : rawItems) {
                    String value = rawItem.isObject() ? text(rawItem) : rawItem.asText("").trim();
                    if (!value.isEmpty()) {
                        items.add(value);
                    }
                }
            } else if (!text.isEmpty()) {
                text.lines().map(String::trim).filter(value -> !value.isEmpty()).forEach(items::add);
            }
            if (items.isEmpty()) {
                return null;
            }
            boolean ordered = truthy(item.get("ordered"))
                    || "ordered".equals(item.path("list_type").asText());
            return new ListBlock(blockId, items, ordered);
        }

        if (itemType.equals("table")) {
            String tableHtml = firstText(item, "table_body", "html").trim();
            tableHtml = rewriteTableAssets(tableHtml, assetUrls);
            tableHtml = rewriteTableFormulas(tableHtml);
            return tableHtml.isEmpty() ? null : new TableBlock(blockId, tableHtml);
        }

        if (itemType.equals("image")) {
            String rawPath = firstText(item, "img_path", "image_path", "src");
            String src = assetUrl(rawPath, assetUrls);
            if (src.isEmpty()) {
                return null;
            }
            JsonNode rawCaption = firstPresent(item, "image_caption", "caption");
            String alt = "";
            if (rawCaption != null && rawCaption.isArray()) {
                List<String> captions = new ArrayList<>();
                rawCaption.forEach(value -> captions.add(value.asText("")));
                alt = String.join(" ", captions);
            } else if (rawCaption != null && !rawCaption.isNull()) {
                alt = rawCaption.asText("");
            }
            return new ImageBlock(blockId, src, alt.isEmpty() ? null : alt);
        }

        if (List.of("equation", "formula", "interline_equation", "inline_equation").contains(itemType)) {
            String latex = normalizeLatex(firstText(item, "latex").isEmpty()
                    ? text : firstText(item, "latex"));
            return latex.isEmpty() ? null : new FormulaBlock(blockId, latex);
        }

        if ((itemType.equals("text") || itemType.equals("paragraph") || itemType.isEmpty()) && !text.isEmpty()) {
            List<String> lines = text.lines().map(String::trim).filter(value -> !value.isEmpty()).toList();
            if (lines.size() > 1 && lines.stream().allMatch(line -> LIST_PREFIX.matcher(line).find())) {
                boolean ordered = lines.stream().allMatch(line -> ORDERED_LIST_PREFIX.matcher(line).find());
                List<String> items = lines.stream().map(line -> LIST_PREFIX.matcher(line).replaceFirst("")).toList();
                return new ListBlock(blockId, items, ordered);
            }
            return new ParagraphBlock(blockId, text);
        }
        return null;
    }

    private List<ObjectNode> mergeLayoutTablePrefixes(JsonNode rawItems) {
        if (rawItems == null || !rawItems.isArray()) {
            return new ArrayList<>();
        }
        List<ObjectNode> items = new ArrayList<>();
        rawItems.forEach(item -> {
            if (item.isObject()) {
                items.add(((ObjectNode) item).deepCopy());
            }
        });

        int tableIndex = 0;
        while (tableIndex < items.size()) {
            ObjectNode tableItem = items.get(tableIndex);
            String tableHtml = firstText(tableItem, "table_body", "html").trim();
            Box tableBox = bbox(tableItem);
            if (!"table".equals(tableItem.path("type").asText()) || tableHtml.isEmpty() || tableBox == null) {
                tableIndex++;
                continue;
            }

            JsonNode pageIndex = tableItem.get("page_idx");
            int prefixStart = tableIndex;
            while (prefixStart > 0) {
                ObjectNode candidate = items.get(prefixStart - 1);
                Box candidateBox = bbox(candidate);
                if (!"text".equals(candidate.path("type").asText("").toLowerCase(Locale.ROOT))
                        || !sameJsonValue(candidate.get("page_idx"), pageIndex)
                        || candidateBox == null
                        || Math.abs(candidateBox.left() - tableBox.left()) > 40
                        || candidateBox.right() > tableBox.right() + 20) {
                    break;
                }
                prefixStart--;
            }

            List<ObjectNode> prefix = new ArrayList<>(items.subList(prefixStart, tableIndex));
            Box lastBox = prefix.isEmpty() ? null : bbox(prefix.get(prefix.size() - 1));
            boolean hasHeading = prefix.stream().anyMatch(item -> truthy(item.get("text_level")));
            boolean wideTable = tableBox.left() <= 250 && tableBox.right() >= 700;
            boolean continuous = lastBox != null
                    && tableBox.top() - lastBox.bottom() >= -10
                    && tableBox.top() - lastBox.bottom() <= 40;
            if (prefix.size() < 3 || !hasHeading || !wideTable || !continuous) {
                tableIndex++;
                continue;
            }

            String merged = prependLayoutRows(tableHtml, prefix, tableBox);
            if (merged.isEmpty()) {
                tableIndex++;
                continue;
            }
            tableItem.put("table_body", merged);
            tableItem.remove("html");
            items.subList(prefixStart, tableIndex).clear();
            tableIndex = prefixStart + 1;
        }
        return items;
    }

    private String prependLayoutRows(String tableHtml, List<ObjectNode> prefix, Box tableBox) {
        Document document = Jsoup.parseBodyFragment(tableHtml);
        document.outputSettings().prettyPrint(false);
        Element table = document.selectFirst("table");
        Element firstRow = table == null ? null : table.selectFirst("tr");
        if (table == null || firstRow == null) {
            return "";
        }
        int columnCount = firstRow.children().stream()
                .filter(cell -> cell.normalName().equals("th") || cell.normalName().equals("td"))
                .mapToInt(cell -> positiveInt(cell.attr("colspan"), 1))
                .sum();
        if (columnCount < 1) {
            return "";
        }
        table.addClass("lesson-layout-table");
        table.attr("data-repeat-header", "false");

        for (ObjectNode item : prefix) {
            String value = text(item);
            if (value.isEmpty()) {
                continue;
            }
            Element row = new Element(Tag.valueOf("tr"), "");
            Element cell = new Element(Tag.valueOf("td"), "");
            cell.attr("colspan", Integer.toString(columnCount));
            cell.addClass("lesson-layout-cell");
            Box itemBox = bbox(item);
            if (truthy(item.get("text_level"))) {
                cell.addClass("lesson-layout-heading-cell");
                cell.appendElement("strong").text(plainText(value));
            } else {
                if (itemBox != null && itemBox.left() >= tableBox.left() + 20
                        && itemBox.right() <= tableBox.right() - 20) {
                    cell.addClass("lesson-layout-centered-cell");
                }
                cell.text(plainText(value));
            }
            row.appendChild(cell);
            firstRow.before(row);
        }
        return table.outerHtml();
    }

    private TextAlignment headingAlignment(ObjectNode item) {
        Box box = bbox(item);
        if (box == null) {
            return TextAlignment.LEFT;
        }
        double pageWidth = Math.max(Math.abs(box.left()), Math.abs(box.right())) <= 1.5 ? 1.0 : 1000.0;
        double blockCenter = (box.left() + box.right()) / 2;
        double blockWidth = box.right() - box.left();
        double tolerance = Math.max(pageWidth * 0.025, Math.min(pageWidth * 0.08, blockWidth * 0.2));
        if (Math.abs(blockCenter - pageWidth / 2) <= tolerance) {
            return TextAlignment.CENTER;
        }
        if (blockCenter >= pageWidth * 0.67) {
            return TextAlignment.RIGHT;
        }
        return TextAlignment.LEFT;
    }

    private String restoreHeadingSpacing(String text, ObjectNode item, JsonNode ocrLayout) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return text;
        }
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length < 2 || ocrLayout == null || !ocrLayout.isArray()) {
            return text;
        }
        int pageIndex = intValue(item.get("page_idx"), 0);
        if (pageIndex < 0 || pageIndex >= ocrLayout.size() || !ocrLayout.get(pageIndex).isArray()) {
            return text;
        }
        Box headingBox = normalizedBbox(item);
        if (headingBox == null) {
            return text;
        }
        double lineHeight = headingBox.bottom() - headingBox.top();
        List<Box> matching = new ArrayList<>();
        for (JsonNode candidate : ocrLayout.get(pageIndex)) {
            Box box = normalizedBbox(candidate);
            if (box == null) {
                continue;
            }
            double overlap = Math.max(0, Math.min(headingBox.bottom(), box.bottom())
                    - Math.max(headingBox.top(), box.top()));
            if (overlap >= Math.min(lineHeight, box.bottom() - box.top()) * 0.5
                    && box.right() >= headingBox.left() - 0.01
                    && box.left() <= headingBox.right() + 0.01) {
                matching.add(box);
            }
        }
        matching.sort(Comparator.comparingDouble(Box::left));
        if (matching.size() != tokens.length) {
            return text;
        }
        StringBuilder restored = new StringBuilder(tokens[0]);
        for (int index = 1; index < tokens.length; index++) {
            double gapRatio = Math.max(0, matching.get(index).left() - matching.get(index - 1).right()) / lineHeight;
            if (gapRatio >= 0.75) {
                int count = Math.min(6, Math.max(1, (int) Math.round(gapRatio)));
                restored.append("　".repeat(count));
            } else {
                restored.append(' ');
            }
            restored.append(tokens[index]);
        }
        return restored.toString();
    }

    private String rewriteTableAssets(String html, Map<String, String> assetUrls) {
        Matcher matcher = TABLE_IMAGE.matcher(html);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String resolved = assetUrl(matcher.group(2), assetUrls);
            String source = resolved.isEmpty() ? matcher.group(2) : resolved;
            matcher.appendReplacement(output, Matcher.quoteReplacement(matcher.group(1) + source + matcher.group(3)));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private String rewriteTableFormulas(String html) {
        Matcher matcher = TABLE_FORMULA.matcher(html);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String raw = Parser.unescapeEntities(
                    matcher.group(1) != null ? matcher.group(1) : matcher.group(2), false);
            Element span = new Element(Tag.valueOf("span"), "");
            span.addClass("lesson-inline-formula");
            span.attr("data-latex", normalizeLatex(raw));
            span.attr("role", "button");
            span.attr("tabindex", "0");
            matcher.appendReplacement(output, Matcher.quoteReplacement(span.outerHtml()));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private String assetUrl(String rawPath, Map<String, String> assetUrls) {
        if (rawPath == null || rawPath.isEmpty()) {
            return "";
        }
        String normalized = rawPath.replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        String direct = assetUrls.get(normalized);
        if (direct != null) {
            return direct;
        }
        int slash = normalized.lastIndexOf('/');
        return assetUrls.getOrDefault(slash < 0 ? normalized : normalized.substring(slash + 1), "");
    }

    private String normalizeLatex(String value) {
        String latex = value == null ? "" : value.trim();
        latex = latex.replaceAll("^\\$+|\\$+$", "").trim();
        return latex.replace("°", "^{\\circ}");
    }

    private Box bbox(JsonNode item) {
        JsonNode values = item == null ? null : item.get("bbox");
        if (values == null || !values.isArray() || values.size() != 4) {
            return null;
        }
        for (JsonNode value : values) {
            if (!value.isNumber()) {
                return null;
            }
        }
        Box box = new Box(values.get(0).asDouble(), values.get(1).asDouble(),
                values.get(2).asDouble(), values.get(3).asDouble());
        return box.right() > box.left() && box.bottom() > box.top() ? box : null;
    }

    private Box normalizedBbox(JsonNode item) {
        Box box = bbox(item);
        if (box == null) {
            return null;
        }
        double scale = Math.max(Math.max(Math.abs(box.left()), Math.abs(box.top())),
                Math.max(Math.abs(box.right()), Math.abs(box.bottom()))) <= 1.5 ? 1.0 : 1000.0;
        return new Box(box.left() / scale, box.top() / scale, box.right() / scale, box.bottom() / scale);
    }

    private String text(JsonNode value) {
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isObject()) {
            String text = firstText(value, "text", "content");
            return text.trim();
        }
        return value.asText("").trim();
    }

    private String firstText(JsonNode object, String... fields) {
        for (String field : fields) {
            JsonNode value = object.get(field);
            if (truthy(value)) {
                return value.asText();
            }
        }
        return "";
    }

    private JsonNode firstPresent(JsonNode object, String... fields) {
        for (String field : fields) {
            JsonNode value = object.get(field);
            if (truthy(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean truthy(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return false;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            return value.asDouble() != 0;
        }
        if (value.isTextual()) {
            return !value.asText().isEmpty();
        }
        return value.size() > 0;
    }

    private boolean sameJsonValue(JsonNode left, JsonNode right) {
        if (left == null || left.isNull()) {
            return right == null || right.isNull();
        }
        return left.equals(right);
    }

    private int positiveOrDefault(JsonNode value, int fallback) {
        int parsed = intValue(value, fallback);
        return parsed > 0 ? parsed : fallback;
    }

    private int intValue(JsonNode value, int fallback) {
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (value.canConvertToInt()) {
            return value.asInt();
        }
        try {
            return Integer.parseInt(value.asText());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private int positiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String plainText(String value) {
        return Parser.unescapeEntities(TAG_PATTERN.matcher(value).replaceAll(""), false).trim();
    }

    private String stem(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private record Box(double left, double top, double right, double bottom) {
    }
}
