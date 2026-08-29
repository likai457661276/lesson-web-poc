package com.lessonweb.lesson.docx.formula;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
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
            case "math", "mrow", "mstyle", "semantics", "annotation" -> children(element);
            case "mi" -> textRun(element.getTextContent(), identifierStyle(element));
            case "mn", "mo", "mtext" -> textRun(element.getTextContent(), "p");
            case "msup" -> binary(element, "sSup", "e", "sup");
            case "msub" -> binary(element, "sSub", "e", "sub");
            case "msubsup" -> ternary(element);
            case "mfrac" -> binary(element, "f", "num", "den");
            case "msqrt" -> radical(children(element), "", true);
            case "mroot" -> root(element);
            case "mfenced" -> fenced(element);
            default -> children(element);
        };
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
        return "<m:d><m:dPr><m:begChr m:val=\"" + escapeAttribute(open) + "\"/><m:endChr m:val=\""
                + escapeAttribute(close) + "\"/></m:dPr><m:e>" + children(element) + "</m:e></m:d>";
    }

    private Node childAt(Element element, int index) {
        return index < element.getChildNodes().getLength() ? element.getChildNodes().item(index) : null;
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
