package com.lessonweb.lesson.docx.formula;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

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
            return textRun(node.getNodeValue());
        }
        if (!(node instanceof Element element)) {
            return "";
        }
        String name = element.getLocalName();
        return switch (name) {
            case "math", "mrow" -> children(element);
            case "mi", "mn", "mo" -> textRun(element.getTextContent());
            case "msup" -> binary(element, "sSup", "e", "sup");
            case "msub" -> binary(element, "sSub", "e", "sub");
            case "msubsup" -> ternary(element);
            case "mfrac" -> binary(element, "f", "num", "den");
            case "msqrt" -> "<m:rad><m:radPr><m:degHide m:val=\"1\"/></m:radPr><m:deg/><m:e>"
                    + children(element) + "</m:e></m:rad>";
            default -> children(element);
        };
    }

    private String binary(Element element, String container, String first, String second) {
        Node a = element.getChildNodes().item(0);
        Node b = element.getChildNodes().item(1);
        return "<m:" + container + "><m:" + first + ">" + render(a) + "</m:" + first + "><m:"
                + second + ">" + render(b) + "</m:" + second + "></m:" + container + ">";
    }

    private String ternary(Element element) {
        return "<m:sSubSup><m:e>" + render(element.getChildNodes().item(0)) + "</m:e><m:sub>"
                + render(element.getChildNodes().item(1)) + "</m:sub><m:sup>"
                + render(element.getChildNodes().item(2)) + "</m:sup></m:sSubSup>";
    }

    private String children(Element element) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < element.getChildNodes().getLength(); index++) {
            result.append(render(element.getChildNodes().item(index)));
        }
        return result.toString();
    }

    private String textRun(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String escaped = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<m:r><m:t>" + escaped + "</m:t></m:r>";
    }
}
