package com.lessonweb.lesson.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lessonweb.lesson.model.lesson.DocumentBlock;
import com.lessonweb.lesson.exception.AppException;
import com.lessonweb.lesson.adapter.MineruGeometry.Box;
import org.springframework.http.HttpStatus;
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

import static com.lessonweb.lesson.adapter.MineruGeometry.normalizedBox;

@Component
public class MineruLessonDocumentAdapter {

    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern LIST_PREFIX = Pattern.compile("^(?:[-*•]|\\d+[.)])\\s+");
    private static final Pattern ORDERED_LIST_PREFIX = Pattern.compile("^\\d+[.)]\\s+");
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
        JsonNode content = result.contentList();
        if (content == null || !content.isArray() || content.isEmpty()) {
            throw new AppException("DOCUMENT_CONTENT_EMPTY", "解析结果没有可用内容，请检查源 PDF 或重新解析", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        for (int index = 0; index < content.size(); index++) {
            JsonNode item = content.get(index);
            if (!item.isObject()) throw incomplete(item, index + 1, "内容块格式无效");
            if ("table".equalsIgnoreCase(item.path("type").asText())
                    && firstText(item, "table_body", "html").isBlank()
                    && isDeletedTableFragment((ObjectNode) item, result.layout())) continue;
            DocumentBlock block = convertItem(
                    (ObjectNode) item, index + 1, assetUrls, result.ocrLayout(), result.layout());
            if (block == null) throw incomplete(item, index + 1, "内容块为空或无法转换");
            blocks.add(block);
            if (block instanceof HeadingBlock heading && heading.level() == 1 && title.equals(defaultTitle)) {
                title = heading.text();
            }
        }
        if (blocks.stream().allMatch(block -> block instanceof ParagraphBlock paragraph && paragraph.text().isBlank())) {
            throw new AppException("DOCUMENT_CONTENT_EMPTY", "解析结果没有可用内容，请检查源 PDF 或重新解析",
                    HttpStatus.UNPROCESSABLE_ENTITY);
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
            JsonNode ocrLayout,
            JsonNode layout
    ) {
        String itemType = item.path("type").asText("").toLowerCase(Locale.ROOT);
        String blockId = String.format("block-%04d", index);
        String text = text(item);

        if (itemType.equals("title") || itemType.equals("heading")
                || ((itemType.equals("text") || itemType.equals("paragraph") || itemType.isEmpty())
                    && truthy(item.get("text_level")))) {
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
                    if (value.isEmpty()) throw incomplete(item, index, "列表包含无法转换的条目");
                    if (rawItem.isObject() && hasStructuredContent(rawItem)) {
                        throw incomplete(item, index, "列表包含尚不支持的嵌套或非文字内容，请对照原文处理");
                    }
                    items.add(value);
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
            tableHtml = rewriteTableFormulas(tableHtml);
            if (!tableHtml.isEmpty()) {
                Document html = Jsoup.parseBodyFragment(tableHtml);
                html.outputSettings().prettyPrint(false);
                Element table = html.selectFirst("table");
                if (table == null || table.selectFirst("td, th") == null) {
                    throw incomplete(item, index, "表格缺少有效单元格");
                }
                for (Element image : html.select("img")) {
                    String src = assetUrl(image.attr("src"), assetUrls);
                    if (src.isEmpty()) throw incomplete(item, index, "表格中的图片资源缺失");
                    image.attr("src", src);
                }
                JsonNode captions = item.path("table_caption");
                if (table.selectFirst("caption") == null && captions.isArray()) {
                    List<String> lines = new ArrayList<>();
                    captions.forEach(caption -> {
                        String value = plainText(caption.asText(""));
                        if (!value.isBlank()) lines.add(value);
                    });
                    if (!lines.isEmpty()) table.prependElement("caption").text(String.join("\n", lines));
                }
                tableHtml = html.body().html();
            }
            if (tableHtml.isEmpty()) throw incomplete(item, index, "表格缺少结构化内容，无法完整转换");
            return new TableBlock(blockId, tableHtml);
        }

        if (itemType.equals("image")) {
            String rawPath = firstText(item, "img_path", "image_path", "src");
            String src = assetUrl(rawPath, assetUrls);
            if (src.isEmpty()) throw incomplete(item, index, "图片资源缺失");
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
            DocumentBlock restored = new MineruReadingOrder().restore(item, layout, blockId);
            if (restored != null) return restored;
            List<String> lines = text.lines().map(String::trim).filter(value -> !value.isEmpty()).toList();
            if (lines.size() > 1 && lines.stream().allMatch(line -> LIST_PREFIX.matcher(line).find())) {
                boolean ordered = lines.stream().allMatch(line -> ORDERED_LIST_PREFIX.matcher(line).find());
                List<String> items = lines.stream().map(line -> LIST_PREFIX.matcher(line).replaceFirst("")).toList();
                return new ListBlock(blockId, items, ordered);
            }
            return new ParagraphBlock(blockId, text);
        }
        if (!text.isEmpty() && !hasStructuredContent(item)) {
            return new ParagraphBlock(blockId, text);
        }
        throw incomplete(item, index, "内容类型或结构尚不支持，无法完整转换");
    }

    private AppException incomplete(JsonNode item, int index, String reason) {
        return new AppException("DOCUMENT_CONTENT_INCOMPLETE", location(item, index) + "：" + reason,
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private boolean hasStructuredContent(JsonNode item) {
        return List.of("img_path", "image_path", "src", "table_body", "html", "latex", "items", "list_items", "blocks", "children")
                .stream().anyMatch(field -> truthy(item.get(field)));
    }

    private boolean isDeletedTableFragment(ObjectNode item, JsonNode layout) {
        // An explicit Provider deletion marker distinguishes merged-page placeholders
        // from tables whose content is genuinely unavailable. Never infer this from page numbers.
        if (!text(item).isEmpty() || !firstText(item, "img_path", "image_path", "src").isEmpty()
                || truthy(item.get("table_caption")) || truthy(item.get("table_footnote"))) return false;
        JsonNode table = MineruGeometry.matchingBlock(item, layout, "para_blocks", "table");
        if (table == null) return false;
        boolean foundBody = false;
        for (JsonNode child : table.path("blocks")) {
            if (!"table_body".equals(child.path("type").asText())) continue;
            foundBody = true;
            if (!child.path("lines_deleted").isBoolean() || !child.path("lines_deleted").asBoolean()
                    || !child.path("lines").isArray() || !child.path("lines").isEmpty()) return false;
        }
        return foundBody;
    }

    private String location(JsonNode item, int index) {
        JsonNode page = item.path("page_idx");
        String prefix = page.isIntegralNumber() && page.canConvertToInt() && page.asInt() >= 0
                ? "第 " + (page.asLong() + 1) + " 页，" : "";
        return prefix + "第 " + index + " 个内容块";
    }

    private TextAlignment headingAlignment(ObjectNode item) {
        Box box = normalizedBox(item);
        if (box == null) {
            return TextAlignment.LEFT;
        }
        double pageWidth = 1.0;
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
        int pageIndex = intValue(item.get("page_idx"), -1);
        if (pageIndex < 0 || pageIndex >= ocrLayout.size() || !ocrLayout.get(pageIndex).isArray()) {
            return text;
        }
        Box headingBox = normalizedBox(item);
        if (headingBox == null) {
            return text;
        }
        double lineHeight = headingBox.bottom() - headingBox.top();
        List<Box> matching = new ArrayList<>();
        for (JsonNode candidate : ocrLayout.get(pageIndex)) {
            Box box = normalizedBox(candidate);
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

}
