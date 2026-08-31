package com.lessonweb.lesson.docx;

import com.lessonweb.lesson.exception.AppException;
import jakarta.xml.bind.JAXBElement;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.openpackaging.parts.WordprocessingML.NumberingDefinitionsPart;
import org.docx4j.wml.ContentAccessor;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.Tc;
import org.docx4j.wml.Text;
import org.docx4j.wml.Tr;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class HtmlToDocxService {

    private static final String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final int CONTENT_WIDTH_DXA = 9360;
    private static final int MAX_TABLE_ROWS = 2_000;
    private static final int MAX_TABLE_COLUMNS = 100;
    private static final int MAX_TABLE_CELLS = 10_000;
    private static final int CELL_MARGIN_DXA = 140;
    private static final Set<String> BLOCK_TAGS = Set.of("p", "pre", "blockquote", "div", "article", "section",
            "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "table", "figure");
    private static final Pattern INVALID_FILENAME = Pattern.compile("[\\x00-\\x1f<>:\"/\\\\|?*]");
    private final DocxFormulaRenderer formulas;
    private final DocxImageRenderer images;
    private final DocxFontService fonts;

    public HtmlToDocxService(DocxFormulaRenderer formulas, DocxImageRenderer images, DocxFontService fonts) {
        this.formulas = formulas;
        this.images = images;
        this.fonts = fonts;
    }

    public ExportResult export(String html, String filename) {
        try {
            Document source = Jsoup.parse(html == null ? "" : html);
            source.select("script,style,noscript,iframe,object,embed,button,input,textarea").remove();
            WordprocessingMLPackage document = WordprocessingMLPackage.createPackage();
            configureDocument(document);
            RenderState state = new RenderState(document);
            appendFlow(source.body(), state);
            if (!state.hasContent) {
                throw new AppException("EMPTY_HTML", "HTML 中没有可导出的内容", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return new ExportResult(fonts.embed(output.toByteArray()), safeFilename(filename));
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AppException("DOCX_EXPORT_FAILED", "DOCX 导出失败", HttpStatus.INTERNAL_SERVER_ERROR, exception);
        }
    }

    private void configureDocument(WordprocessingMLPackage document) throws Exception {
        MainDocumentPart main = document.getMainDocumentPart();
        main.getStyleDefinitionsPart().setJaxbElement((org.docx4j.wml.Styles) XmlUtils.unmarshalString(fonts.stylesXml()));
        Object section = XmlUtils.unmarshalString("""
                <w:sectPr xmlns:w=\"%s\"><w:pgSz w:w=\"12240\" w:h=\"15840\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/></w:sectPr>
                """.formatted(W_NS));
        main.getJaxbElement().getBody().setSectPr((org.docx4j.wml.SectPr) section);

        NumberingDefinitionsPart numbering = main.getNumberingDefinitionsPart();
        if (numbering == null) {
            numbering = new NumberingDefinitionsPart();
            numbering.setJaxbElement((org.docx4j.wml.Numbering) XmlUtils.unmarshalString(numberingXml()));
            main.addTargetPart(numbering);
        } else {
            numbering.setJaxbElement((org.docx4j.wml.Numbering) XmlUtils.unmarshalString(numberingXml()));
        }
    }

    /** Buffer adjacent inline nodes, flushing at real block boundaries in body, cells and list items. */
    private void appendFlow(Element parent, RenderState state) throws Exception {
        P inline = paragraph(null, null, null);
        for (Node child : parent.childNodes()) {
            if (child instanceof Element element && (BLOCK_TAGS.contains(element.normalName())
                    || element.hasClass("lesson-formula")
                    || ("img".equals(element.normalName()) && parent.closest("td, th, li") == null))) {
                if (hasMeaningfulContent(inline)) state.add(inline);
                inline = paragraph(null, null, null);
                appendBlock(element, state);
            } else {
                if (child instanceof TextNode text && text.isBlank() && !hasMeaningfulContent(inline)) continue;
                appendInline(child, inline, state.style, state);
            }
        }
        if (hasMeaningfulContent(inline)) state.add(inline);
    }

    private void appendBlock(Element element, RenderState state) throws Exception {
        if (element.hasClass("document-title")) {
            return;
        }
        String tag = element.normalName();
        if (tag.matches("h[1-6]")) {
            int level = Integer.parseInt(tag.substring(1));
            boolean lesson = element.hasClass("lesson-heading");
            String style = element.hasAttr("data-docx-title") || (level == 1 && !state.hasContent && !lesson)
                    ? "HTMLTitle" : lesson ? "LessonHeading" + level : "Heading" + level;
            P paragraph = paragraph(style, alignment(element), null);
            appendInline(element, paragraph, state.style, state);
            if (hasMeaningfulContent(paragraph)) {
                state.add(paragraph);
            }
            return;
        }
        if (Set.of("p", "pre", "blockquote").contains(tag)) {
            P paragraph = paragraph(null, null, null);
            if ("pre".equals(tag)) {
                appendText(paragraph, element.wholeText(), new InlineStyle(false, false, false, false, false, "Courier New", null));
            } else {
                appendInline(element, paragraph, state.style, state);
            }
            if ("blockquote".equals(tag)) {
                paragraph.setPPr((org.docx4j.wml.PPr) XmlUtils.unmarshalString("<w:pPr xmlns:w=\"" + W_NS + "\"><w:ind w:left=\"432\" w:right=\"432\"/></w:pPr>"));
            }
            if (hasMeaningfulContent(paragraph)) {
                state.add(paragraph);
            }
            return;
        }
        if ("ul".equals(tag) || "ol".equals(tag)) {
            appendList(element, state);
            return;
        }
        if ("table".equals(tag)) {
            Element caption = directChildren(element, Set.of("caption")).stream().findFirst().orElse(null);
            if (caption != null && !caption.text().isBlank()) {
                P paragraph = paragraph("Caption", null, null);
                paragraph.getPPr().setKeepNext(new org.docx4j.wml.BooleanDefaultTrue());
                appendInline(caption, paragraph, state.style, state);
                state.add(paragraph);
            }
            Object table = buildTable(element, state);
            if (table != null) {
                state.add(table);
            }
            return;
        }
        if ("figure".equals(tag)) {
            Element image = element.selectFirst("img");
            if (image != null) {
                appendStandaloneImage(image, state);
            }
            Element caption = element.selectFirst("figcaption");
            if (caption != null && !caption.text().isBlank()) {
                P paragraph = paragraph("Caption", "center", null);
                appendInline(caption, paragraph, InlineStyle.PLAIN, state);
                state.add(paragraph);
            }
            return;
        }
        if ("img".equals(tag)) {
            appendStandaloneImage(element, state);
            return;
        }
        if (element.hasAttr("data-latex") || element.hasClass("lesson-formula")) {
            P paragraph = paragraph(null, "center", null);
            appendInline(element, paragraph, state.style, state);
            state.add(paragraph);
            return;
        }
        appendFlow(element, state);
    }

    private void appendInline(Node node, P paragraph, InlineStyle style, RenderState state) throws Exception {
        appendInline(node, paragraph, style, state, state.availableWidth);
    }

    private void appendInline(Node node, P paragraph, InlineStyle style, RenderState state, int availableWidth) throws Exception {
        if (node instanceof TextNode textNode) {
            String text = textNode.getWholeText();
            if (!preservesWhitespace(textNode)) text = text.replaceAll("[\\t\\r\\n ]+", " ");
            if (!text.isEmpty()) {
                appendText(paragraph, text, style);
            }
            return;
        }
        if (!(node instanceof Element element)) {
            return;
        }
        if (element.hasAttr("data-latex")) {
            appendFormula(paragraph, element.attr("data-latex"));
            return;
        }
        String tag = element.normalName();
        if ("br".equals(tag)) {
            paragraph.getContent().add(XmlUtils.unmarshalString("<w:r xmlns:w=\"" + W_NS + "\"><w:br/></w:r>"));
            return;
        }
        if ("img".equals(tag)) {
            images.append(state.document, paragraph, element, availableWidth);
            return;
        }
        if (Set.of("script", "style", "button", "input", "textarea", "svg").contains(tag)) {
            return;
        }
        InlineStyle next = style.merge(tag);
        for (Node child : element.childNodes()) {
            appendInline(child, paragraph, next, state, availableWidth);
        }
    }

    private boolean preservesWhitespace(Node node) {
        for (Node ancestor = node.parent(); ancestor instanceof Element element; ancestor = ancestor.parent()) {
            if (element.hasClass("lesson-heading") || element.hasClass("lesson-paragraph")
                    || "pre".equals(element.normalName()) || "caption".equals(element.normalName())) return true;
        }
        return false;
    }

    private void appendList(Element list, RenderState state) throws Exception {
        int level = state.listLevel;
        if (level > 8 || list.hasAttr("reversed") || list.hasAttr("type")) {
            throw new AppException("DOCX_LIST_UNSUPPORTED", "列表嵌套层级或编号样式不受支持", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        int numId = newListNumbering(list, state, level);
        for (Element item : directChildren(list, Set.of("li"))) {
            if (item.hasAttr("value")) throw new AppException("DOCX_LIST_UNSUPPORTED", "列表条目自定义编号不受支持", HttpStatus.UNPROCESSABLE_ENTITY);
            List<Object> contents = new ArrayList<>();
            RenderState itemState = new RenderState(state.document, contents, state.availableWidth, state.style, level + 1);
            appendFlow(item, itemState);
            P first = contents.isEmpty() ? null : unwrap(contents.get(0), P.class);
            if (first == null || (first.getPPr() != null && first.getPPr().getNumPr() != null)) {
                first = paragraph(null, null, null);
                contents.add(0, first);
            }
            if (first.getPPr() == null) first.setPPr(new org.docx4j.wml.PPr());
            var numbered = (org.docx4j.wml.PPr) XmlUtils.unmarshalString("<w:pPr xmlns:w=\"" + W_NS + "\"><w:numPr><w:ilvl w:val=\"" + level + "\"/><w:numId w:val=\"" + numId + "\"/></w:numPr></w:pPr>");
            first.getPPr().setNumPr(numbered.getNumPr());
            for (Object content : contents) {
                P continuation = unwrap(content, P.class);
                if (continuation != null && continuation != first) {
                    if (continuation.getPPr() == null) continuation.setPPr(new org.docx4j.wml.PPr());
                    if (continuation.getPPr().getNumPr() == null && continuation.getPPr().getInd() == null) {
                        var indent = new org.docx4j.wml.PPrBase.Ind();
                        indent.setLeft(java.math.BigInteger.valueOf(720 + level * 360));
                        continuation.getPPr().setInd(indent);
                    }
                }
                state.add(content);
            }
        }
    }

    private int newListNumbering(Element list, RenderState state, int level) throws Exception {
        var numbering = state.document.getMainDocumentPart().getNumberingDefinitionsPart().getJaxbElement();
        int numId = numbering.getNum().size() + 1;
        int start = 1;
        try {
            if (list.hasAttr("start")) start = Integer.parseInt(list.attr("start"));
        } catch (NumberFormatException exception) {
            throw new AppException("DOCX_LIST_UNSUPPORTED", "列表起始编号无效", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        var num = (org.docx4j.wml.Numbering.Num) XmlUtils.unmarshalString("<w:num xmlns:w=\"" + W_NS + "\" w:numId=\"" + numId
                + "\"><w:abstractNumId w:val=\"" + ("ol".equals(list.normalName()) ? 2 : 1)
                + "\"/><w:lvlOverride w:ilvl=\"" + level + "\"><w:startOverride w:val=\"" + start + "\"/></w:lvlOverride></w:num>");
        numbering.getNum().add(num);
        return numId;
    }

    private Object buildTable(Element source, RenderState state) throws Exception {
        List<Element> rows = source.select("tr").stream().filter(row -> row.closest("table") == source).toList();
        if (rows.isEmpty()) {
            return null;
        }
        if (rows.size() > MAX_TABLE_ROWS) throw tableTooLarge();
        List<Placement> placements = new ArrayList<>();
        Set<String> occupied = new HashSet<>();
        int columns = 0;
        for (int row = 0; row < rows.size(); row++) {
            int column = 0;
            for (Element cell : directChildren(rows.get(row), Set.of("th", "td"))) {
                while (occupied.contains(row + ":" + column)) column++;
                int rowspan = tableSpan(cell.attr("rowspan"), MAX_TABLE_ROWS);
                int colspan = tableSpan(cell.attr("colspan"), MAX_TABLE_COLUMNS);
                if (column + colspan > MAX_TABLE_COLUMNS
                        || (long) rows.size() * (column + colspan) > MAX_TABLE_CELLS) {
                    throw tableTooLarge();
                }
                placements.add(new Placement(row, column, rowspan, colspan, cell));
                for (int r = row; r < Math.min(rows.size(), row + rowspan); r++) {
                    for (int c = column; c < column + colspan; c++) occupied.add(r + ":" + c);
                }
                column += colspan;
            }
            columns = Math.max(columns, column);
        }
        if (columns == 0) return null;
        int[] widths = tableWidths(placements, columns, state.availableWidth);
        List<TableCellContent> cellContents = new ArrayList<>();
        StringBuilder xml = new StringBuilder("<w:tbl xmlns:w=\"").append(W_NS).append("\" xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">");
        int indent = source.closest("td, th") == null ? CELL_MARGIN_DXA : 0;
        xml.append("<w:tblPr><w:tblW w:w=\"").append(state.availableWidth).append("\" w:type=\"dxa\"/><w:tblInd w:w=\"").append(indent).append("\" w:type=\"dxa\"/><w:tblLayout w:type=\"fixed\"/><w:tblBorders><w:top w:val=\"single\" w:sz=\"4\"/><w:left w:val=\"single\" w:sz=\"4\"/><w:bottom w:val=\"single\" w:sz=\"4\"/><w:right w:val=\"single\" w:sz=\"4\"/><w:insideH w:val=\"single\" w:sz=\"4\"/><w:insideV w:val=\"single\" w:sz=\"4\"/></w:tblBorders><w:tblCellMar><w:top w:w=\"120\" w:type=\"dxa\"/><w:start w:w=\"140\" w:type=\"dxa\"/><w:bottom w:w=\"120\" w:type=\"dxa\"/><w:end w:w=\"140\" w:type=\"dxa\"/></w:tblCellMar></w:tblPr><w:tblGrid>");
        for (int width : widths) xml.append("<w:gridCol w:w=\"").append(width).append("\"/>");
        xml.append("</w:tblGrid>");
        for (int row = 0; row < rows.size(); row++) {
            xml.append("<w:tr>");
            int renderedCell = 0;
            if (row == 0 && !"false".equalsIgnoreCase(source.attr("data-repeat-header")) && !directChildren(rows.get(0), Set.of("th")).isEmpty()) {
                xml.append("<w:trPr><w:tblHeader w:val=\"true\"/></w:trPr>");
            }
            for (int column = 0; column < columns; column++) {
                Placement start = findStart(placements, row, column);
                Placement spanning = findSpanning(placements, row, column);
                if (start == null && spanning != null && spanning.column != column) continue;
                Placement effective = start != null ? start : spanning;
                int cellWidth = 0;
                int span = effective == null ? 1 : effective.colspan;
                for (int index = column; index < Math.min(columns, column + span); index++) cellWidth += widths[index];
                xml.append("<w:tc><w:tcPr><w:tcW w:w=\"").append(cellWidth).append("\" w:type=\"dxa\"/>");
                if (effective != null && effective.colspan > 1) xml.append("<w:gridSpan w:val=\"").append(effective.colspan).append("\"/>");
                if (start != null && start.rowspan > 1) xml.append("<w:vMerge w:val=\"restart\"/>");
                else if (spanning != null && spanning.row < row) xml.append("<w:vMerge/>");
                if (start != null && "th".equals(start.cell.normalName())) xml.append("<w:shd w:fill=\"F2F4F7\"/>");
                String verticalAlignment = effective != null && "th".equals(effective.cell.normalName()) ? "center" : "top";
                xml.append("<w:vAlign w:val=\"").append(verticalAlignment).append("\"/></w:tcPr><w:p><w:pPr><w:spacing w:after=\"0\"/>");
                xml.append("</w:pPr>");
                if (start != null) {
                    boolean header = "th".equals(start.cell.normalName());
                    cellContents.add(new TableCellContent(row, renderedCell, start.cell, header, cellWidth - 2 * CELL_MARGIN_DXA));
                }
                xml.append("</w:p></w:tc>");
                renderedCell++;
            }
            xml.append("</w:tr>");
        }
        xml.append("</w:tbl>");
        Tbl table = (Tbl) XmlUtils.unmarshalString(xml.toString());
        populateTableCells(table, cellContents, state);
        return table;
    }

    private void populateTableCells(Tbl table, List<TableCellContent> cellContents, RenderState state) throws Exception {
        List<Tr> rows = table.getContent().stream().map(value -> unwrap(value, Tr.class)).filter(java.util.Objects::nonNull).toList();
        for (TableCellContent entry : cellContents) {
            if (entry.row >= rows.size()) continue;
            List<Tc> cells = rows.get(entry.row).getContent().stream()
                    .map(value -> unwrap(value, Tc.class))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (entry.cell >= cells.size()) continue;
            Tc cell = cells.get(entry.cell);
            cell.getContent().clear();
            InlineStyle style = entry.bold ? state.style.merge("th") : state.style;
            RenderState cellState = new RenderState(state.document, cell.getContent(), entry.availableWidthDxa, style, 0);
            appendFlow(entry.content, cellState);
            // Word requires a final paragraph, including after a nested table.
            if (cell.getContent().isEmpty() || unwrap(cell.getContent().get(cell.getContent().size() - 1), P.class) == null) {
                cellState.add(paragraph(null, null, null));
            }
            for (Object content : cell.getContent()) {
                P paragraph = unwrap(content, P.class);
                if (paragraph != null && paragraph.getPPr() == null) {
                    paragraph.setPPr((org.docx4j.wml.PPr) XmlUtils.unmarshalString("<w:pPr xmlns:w=\"" + W_NS + "\"><w:spacing w:after=\"0\"/></w:pPr>"));
                }
            }
        }
    }

    private <T> T unwrap(Object value, Class<T> type) {
        if (type.isInstance(value)) return type.cast(value);
        if (value instanceof JAXBElement<?> element && type.isInstance(element.getValue())) {
            return type.cast(element.getValue());
        }
        return null;
    }

    private void appendStandaloneImage(Element image, RenderState state) throws Exception {
        P paragraph = paragraph(null, "center", null);
        images.append(state.document, paragraph, image, state.availableWidth);
        state.add(paragraph);
    }

    private void appendFormula(P paragraph, String latex) {
        formulas.append(paragraph, latex);
    }

    private P paragraph(String style, String alignment, String text) throws Exception {
        StringBuilder properties = new StringBuilder();
        if (style != null) properties.append("<w:pStyle w:val=\"").append(style).append("\"/>");
        if (alignment != null) properties.append("<w:jc w:val=\"").append(alignment).append("\"/>");
        P paragraph = new P();
        if (!properties.isEmpty()) {
            paragraph.setPPr((org.docx4j.wml.PPr) XmlUtils.unmarshalString("<w:pPr xmlns:w=\"" + W_NS + "\">" + properties + "</w:pPr>"));
        }
        if (text != null) appendText(paragraph, text, InlineStyle.PLAIN);
        return paragraph;
    }

    private void appendText(ContentAccessor target, String value, InlineStyle style) {
        if (value == null || value.isEmpty()) return;
        R run = new R();
        try {
            run.setRPr((org.docx4j.wml.RPr) XmlUtils.unmarshalString(runProperties(style)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        String[] pieces = value.replace("\r\n", "\n").replace('\r', '\n').split("(?=[\\n\\t])|(?<=[\\n\\t])", -1);
        for (String piece : pieces) {
            if (piece.equals("\n")) run.getContent().add(new org.docx4j.wml.Br());
            else if (piece.equals("\t")) run.getContent().add(new R.Tab());
            else if (!piece.isEmpty()) {
                Text text = new Text();
                text.setValue(piece);
                text.setSpace("preserve");
                run.getContent().add(text);
            }
        }
        target.getContent().add(run);
    }

    private String runProperties(InlineStyle style) {
        String font = style.font == null ? "Noto Sans SC" : style.font;
        StringBuilder xml = new StringBuilder("<w:rPr xmlns:w=\"").append(W_NS).append("\"><w:rFonts w:ascii=\"").append(font).append("\" w:hAnsi=\"").append(font).append("\" w:eastAsia=\"Noto Sans SC\"/>");
        if (style.bold) xml.append("<w:b/>");
        if (style.italic) xml.append("<w:i/>");
        if (style.underline) xml.append("<w:u w:val=\"single\"/>");
        if (style.sup) xml.append("<w:vertAlign w:val=\"superscript\"/>");
        if (style.sub) xml.append("<w:vertAlign w:val=\"subscript\"/>");
        if (style.color != null) xml.append("<w:color w:val=\"").append(style.color).append("\"/>");
        return xml.append("</w:rPr>").toString();
    }

    private boolean hasMeaningfulContent(P paragraph) {
        return !paragraph.getContent().isEmpty();
    }

    private String alignment(Element element) {
        if (element.hasClass("lesson-heading-center")) return "center";
        if (element.hasClass("lesson-heading-right")) return "right";
        if (element.hasClass("lesson-heading-left")) return "left";
        return null;
    }

    private int tableSpan(String value, int maximum) {
        if (value.isBlank()) return 1;
        try {
            long span = Long.parseLong(value);
            if (span > maximum) throw tableTooLarge();
            return (int) Math.max(1, span);
        } catch (NumberFormatException exception) {
            throw new AppException("INVALID_TABLE_SPAN", "表格跨行或跨列值无效", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private AppException tableTooLarge() {
        return new AppException("TABLE_TOO_LARGE", "表格超出导出限制（2000 行、100 列、10000 个展开单元格）",
                HttpStatus.PAYLOAD_TOO_LARGE);
    }

    private int[] tableWidths(List<Placement> placements, int count, int availableWidth) {
        double[] loads = new double[count];
        for (Placement placement : placements) {
            // Full-width banners do not constrain relative column widths. Spread
            // other merged cells across their columns; do not cap narrative text.
            if (placement.colspan == count) continue;
            double content = placement.cell.text().codePointCount(0, placement.cell.text().length());
            content += placement.cell.select("img").size() * 40;
            for (int index = placement.column; index < Math.min(count, placement.column + placement.colspan); index++) {
                loads[index] += content / placement.colspan;
            }
        }
        // Square-root weighting balances total wrapping without starving short
        // columns. Reserve a minimum before distributing the remaining width.
        double[] weights = java.util.Arrays.stream(loads).map(load -> Math.sqrt(Math.max(8, load))).toArray();
        double total = java.util.Arrays.stream(weights).sum();
        if (availableWidth < count * (2 * CELL_MARGIN_DXA + 1)) {
            throw new AppException("DOCX_TABLE_TOO_NARROW", "表格列数或嵌套深度超出当前纸张可用宽度", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        int minimum = Math.min(720, availableWidth / count);
        int remaining = availableWidth - minimum * count;
        int[] widths = new int[count];
        int used = 0;
        for (int index = 0; index < count; index++) {
            widths[index] = minimum + (int) Math.floor(remaining * weights[index] / total);
            used += widths[index];
        }
        widths[count - 1] += availableWidth - used;
        return widths;
    }

    private Placement findStart(List<Placement> placements, int row, int column) {
        return placements.stream().filter(p -> p.row == row && p.column == column).findFirst().orElse(null);
    }

    private Placement findSpanning(List<Placement> placements, int row, int column) {
        return placements.stream().filter(p -> row >= p.row && row < p.row + p.rowspan && column >= p.column && column < p.column + p.colspan).findFirst().orElse(null);
    }

    private List<Element> directChildren(Element parent, Set<String> tags) {
        return parent.children().stream().filter(child -> tags.contains(child.normalName())).toList();
    }

    private String safeFilename(String filename) {
        String source = filename == null ? "lesson" : filename.replace('\\', '/');
        String name = Path.of(source).getFileName().toString().trim();
        name = INVALID_FILENAME.matcher(name).replaceAll("_");
        if (name.toLowerCase(Locale.ROOT).endsWith(".docx")) name = name.substring(0, name.length() - 5);
        name = name.replaceAll("^[ .]+|[ .]+$", "");
        if (name.isBlank()) name = "lesson";
        return name.substring(0, Math.min(160, name.length())) + ".docx";
    }

    private String numberingXml() {
        StringBuilder xml = new StringBuilder("<w:numbering xmlns:w=\"").append(W_NS).append("\">");
        for (int ordered = 0; ordered < 2; ordered++) {
            int id = ordered + 1;
            xml.append("<w:abstractNum w:abstractNumId=\"").append(id).append("\"><w:multiLevelType w:val=\"multilevel\"/>");
            for (int level = 0; level < 9; level++) {
                xml.append("<w:lvl w:ilvl=\"").append(level).append("\"><w:start w:val=\"1\"/><w:numFmt w:val=\"").append(ordered == 1 ? "decimal" : "bullet").append("\"/><w:lvlText w:val=\"").append(ordered == 1 ? "%" + (level + 1) + "." : "•").append("\"/><w:suff w:val=\"tab\"/><w:pPr><w:tabs><w:tab w:val=\"num\" w:pos=\"").append(720 + level * 360).append("\"/></w:tabs><w:ind w:left=\"").append(720 + level * 360).append("\" w:hanging=\"360\"/></w:pPr></w:lvl>");
            }
            xml.append("</w:abstractNum>");
        }
        return xml.append("</w:numbering>").toString();
    }

    public record ExportResult(byte[] content, String filename) {}
    private record Placement(int row, int column, int rowspan, int colspan, Element cell) {}
    private record TableCellContent(int row, int cell, Element content, boolean bold, int availableWidthDxa) {}
    private static final class RenderState {
        private final WordprocessingMLPackage document;
        private final List<Object> content;
        private final int availableWidth;
        private final InlineStyle style;
        private final int listLevel;
        private boolean hasContent;
        private RenderState(WordprocessingMLPackage document) {
            this(document, document.getMainDocumentPart().getContent(), CONTENT_WIDTH_DXA, InlineStyle.PLAIN, 0);
        }
        private RenderState(WordprocessingMLPackage document, List<Object> content, int availableWidth, InlineStyle style, int listLevel) {
            this.document = document;
            this.content = content;
            this.availableWidth = availableWidth;
            this.style = style;
            this.listLevel = listLevel;
        }
        private void add(Object value) { content.add(value); hasContent = true; }
    }
    private record InlineStyle(boolean bold, boolean italic, boolean underline, boolean sup, boolean sub, String font, String color) {
        private static final InlineStyle PLAIN = new InlineStyle(false, false, false, false, false, null, null);
        private InlineStyle merge(String tag) {
            return new InlineStyle(bold || Set.of("b", "strong", "th").contains(tag), italic || Set.of("i", "em", "cite").contains(tag), underline || Set.of("u", "a").contains(tag), sup || "sup".equals(tag), sub || "sub".equals(tag), font, "a".equals(tag) ? "05665C" : color);
        }
    }
}
