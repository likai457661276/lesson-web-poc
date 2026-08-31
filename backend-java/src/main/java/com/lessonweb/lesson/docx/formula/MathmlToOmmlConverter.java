package com.lessonweb.lesson.docx.formula;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class MathmlToOmmlConverter {

    public String convert(String mathml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(mathml)));
            return "<m:oMath xmlns:m=\"http://schemas.openxmlformats.org/officeDocument/2006/math\">"
                    + render(document.getDocumentElement()) + "</m:oMath>";
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to convert MathML", exception);
        }
    }

    private String render(Node node) {
        if (node.getNodeType() == Node.TEXT_NODE) {
            return textRun(node.getNodeValue(), "p");
        }
        if (!(node instanceof Element element)) {
            return "";
        }
        String name = element.getLocalName();
        return switch (name) {
            case "math", "mrow", "mstyle" -> children(element);
            case "semantics" -> render(childAt(element, 0));
            case "mi" -> textRun(element.getTextContent(), identifierStyle(element));
            case "mn", "mo", "mtext" -> textRun(element.getTextContent(), "p");
            case "msup" -> binary(element, "sSup", "e", "sup");
            case "msub" -> binary(element, "sSub", "e", "sub");
            case "msubsup" -> ternary(element);
            case "mfrac" -> binary(element, "f", "num", "den");
            case "msqrt" -> radical(children(element), "", true);
            case "mroot" -> root(element);
            case "mfenced" -> fenced(element);
            case "mover", "munder" -> accent(element);
            default -> throw new IllegalArgumentException("Unsupported MathML element: " + name);
        };
    }

    private String accent(Element element) {
        boolean over = "mover".equals(element.getLocalName());
        Node mark = childAt(element, 1);
        if (!(mark instanceof Element symbol) || !"mo".equals(symbol.getLocalName())) {
            throw new IllegalArgumentException("Unsupported formula decoration");
        }
        String value = symbol.getTextContent();
        String expression = render(childAt(element, 0));
        if (Set.of("¯", "‾", "\u0305", "_", "\u0332").contains(value)) {
            return "<m:bar><m:barPr><m:pos m:val=\"" + (over ? "top" : "bot")
                    + "\"/></m:barPr><m:e>" + expression + "</m:e></m:bar>";
        }
        if (over && "true".equals(element.getAttribute("accent")) && value.codePointCount(0, value.length()) == 1) {
            return "<m:acc><m:accPr><m:chr m:val=\"" + escapeAttribute(value)
                    + "\"/></m:accPr><m:e>" + expression + "</m:e></m:acc>";
        }
        throw new IllegalArgumentException("Unsupported formula decoration");
    }

    private String binary(Element element, String container, String first, String second) {
        Node a = childAt(element, 0);
        Node b = childAt(element, 1);
        return "<m:" + container + "><m:" + first + ">" + render(a) + "</m:" + first + "><m:"
                + second + ">" + render(b) + "</m:" + second + "></m:" + container + ">";
    }

    private String ternary(Element element) {
        return "<m:sSubSup><m:e>" + render(childAt(element, 0)) + "</m:e><m:sub>"
                + render(childAt(element, 1)) + "</m:sub><m:sup>"
                + render(childAt(element, 2)) + "</m:sup></m:sSubSup>";
    }

    private String children(Element element) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < element.getChildNodes().getLength(); index++) {
            result.append(render(element.getChildNodes().item(index)));
        }
        return result.toString();
    }

    private String root(Element element) {
        return radical(render(childAt(element, 0)), render(childAt(element, 1)), false);
    }

    private String radical(String expression, String degree, boolean hideDegree) {
        String properties = hideDegree ? "<m:radPr><m:degHide m:val=\"1\"/></m:radPr>" : "";
        String degreeXml = hideDegree ? "<m:deg/>" : "<m:deg>" + degree + "</m:deg>";
        return "<m:rad>" + properties + degreeXml + "<m:e>" + expression + "</m:e></m:rad>";
    }

    private String fenced(Element element) {
        String open = element.hasAttribute("open") ? element.getAttribute("open") : "(";
        String close = element.hasAttribute("close") ? element.getAttribute("close") : ")";
        String separators = element.hasAttribute("separators") ? element.getAttribute("separators").replaceAll("\\s", "") : ",";
        int[] marks = separators.codePoints().toArray();
        StringBuilder body = new StringBuilder();
        List<Element> children = elementChildren(element);
        for (int index = 0; index < children.size(); index++) {
            if (index > 0 && marks.length > 0) body.append(textRun(new String(Character.toChars(marks[Math.min(index - 1, marks.length - 1)])), "p"));
            body.append(render(children.get(index)));
        }
        return "<m:d><m:dPr><m:begChr m:val=\"" + escapeAttribute(open) + "\"/><m:endChr m:val=\""
                + escapeAttribute(close) + "\"/></m:dPr><m:e>" + body + "</m:e></m:d>";
    }

    private Node childAt(Element element, int index) {
        List<Element> children = elementChildren(element);
        if (index >= children.size()) throw new IllegalArgumentException("Missing formula operand");
        return children.get(index);
    }

    private List<Element> elementChildren(Element element) {
        List<Element> result = new ArrayList<>();
        for (int index = 0; index < element.getChildNodes().getLength(); index++) {
            if (element.getChildNodes().item(index) instanceof Element child) result.add(child);
        }
        return result;
    }

    private String identifierStyle(Element element) {
        String value = element.getTextContent();
        if ("normal".equals(element.getAttribute("mathvariant")) || Set.of("sin", "cos", "tan", "cot", "sec", "csc", "log", "ln", "lim", "max", "min").contains(value)) {
            return "p";
        }
        return "i";
    }

    private String textRun(String value, String style) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String escaped = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<m:r><m:rPr><m:sty m:val=\"" + style + "\"/></m:rPr><m:t>" + escaped + "</m:t></m:r>";
    }

    private String escapeAttribute(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
