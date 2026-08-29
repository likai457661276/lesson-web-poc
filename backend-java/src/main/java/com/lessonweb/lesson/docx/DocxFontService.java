package com.lessonweb.lesson.docx;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Component
public class DocxFontService {

    private static final String FONT_FAMILY = "Noto Sans SC";

    public byte[] embed(byte[] docx) throws IOException {
        byte[] regular = loadFont("fonts/NotoSansSC-Regular.otf");
        byte[] bold = loadFont("fonts/NotoSansSC-Bold.otf");
        UUID regularKey = UUID.nameUUIDFromBytes(regular);
        UUID boldKey = UUID.nameUUIDFromBytes(bold);

        Map<String, byte[]> entries = readZip(docx);
        entries.put("word/fonts/NotoSansSC-regular.odttf", obfuscate(regular, regularKey));
        entries.put("word/fonts/NotoSansSC-bold.odttf", obfuscate(bold, boldKey));
        entries.put("word/styles.xml", stylesXml().getBytes(StandardCharsets.UTF_8));
        entries.put("word/settings.xml", settingsXml().getBytes(StandardCharsets.UTF_8));
        entries.put("word/fontTable.xml", fontTableXml(regularKey, boldKey).getBytes(StandardCharsets.UTF_8));
        entries.put("word/_rels/fontTable.xml.rels", fontRelationshipsXml().getBytes(StandardCharsets.UTF_8));
        entries.put("word/_rels/document.xml.rels", addFontTableRelationship(entries.get("word/_rels/document.xml.rels")));
        entries.put("[Content_Types].xml", addFontContentType(entries.get("[Content_Types].xml")));
        return writeZip(entries);
    }

    private byte[] loadFont(String path) throws IOException {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return input.readAllBytes();
        }
    }

    private Map<String, byte[]> readZip(byte[] source) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(source))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }

    private byte[] writeZip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private byte[] obfuscate(byte[] font, UUID key) {
        byte[] result = font.clone();
        byte[] guid = uuidBytes(key);
        for (int index = 0; index < Math.min(32, result.length); index++) {
            result[index] ^= guid[15 - (index % 16)];
        }
        return result;
    }

    private byte[] uuidBytes(UUID uuid) {
        byte[] result = new byte[16];
        long high = uuid.getMostSignificantBits();
        long low = uuid.getLeastSignificantBits();
        for (int index = 0; index < 8; index++) {
            result[index] = (byte) (high >>> (56 - index * 8));
            result[index + 8] = (byte) (low >>> (56 - index * 8));
        }
        return result;
    }

    private byte[] addFontContentType(byte[] contentTypes) {
        String xml = new String(contentTypes, StandardCharsets.UTF_8);
        if (!xml.contains("Extension=\"odttf\"")) {
            xml = xml.replace("</Types>", "<Default Extension=\"odttf\" ContentType=\"application/vnd.openxmlformats-officedocument.obfuscatedFont\"/></Types>");
        }
        if (!xml.contains("PartName=\"/word/fontTable.xml\"")) {
            xml = xml.replace("</Types>", "<Override PartName=\"/word/fontTable.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.fontTable+xml\"/></Types>");
        }
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] addFontTableRelationship(byte[] relationships) {
        String xml = new String(relationships, StandardCharsets.UTF_8);
        if (!xml.contains("relationships/fontTable")) {
            xml = xml.replace("</Relationships>", "<Relationship Id=\"rIdEmbeddedFontTable\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/fontTable\" Target=\"fontTable.xml\"/></Relationships>");
        }
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private String fontTableXml(UUID regularKey, UUID boldKey) {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:fonts xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <w:font w:name="Noto Sans SC"><w:altName w:val="Microsoft YaHei"/><w:charset w:val="86"/><w:family w:val="swiss"/><w:embedRegular r:id="rIdNotoRegular" w:fontKey="{%s}"/><w:embedBold r:id="rIdNotoBold" w:fontKey="{%s}"/></w:font>
                  <w:font w:name="Cambria Math"><w:family w:val="roman"/></w:font>
                  <w:font w:name="Courier New"><w:family w:val="modern"/></w:font>
                </w:fonts>
                """.formatted(regularKey.toString().toUpperCase(), boldKey.toString().toUpperCase());
    }

    private String fontRelationshipsXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rIdNotoRegular" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/font" Target="fonts/NotoSansSC-regular.odttf"/>
                  <Relationship Id="rIdNotoBold" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/font" Target="fonts/NotoSansSC-bold.odttf"/>
                </Relationships>
                """;
    }

    private String settingsXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:settings xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:embedTrueTypeFonts/><w:saveSubsetFonts/><w:themeFontLang w:val="en-US" w:eastAsia="zh-CN"/></w:settings>
                """;
    }

    String stylesXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Noto Sans SC" w:hAnsi="Noto Sans SC" w:eastAsia="Noto Sans SC" w:cs="Noto Sans SC"/><w:sz w:val="22"/><w:lang w:val="en-US" w:eastAsia="zh-CN"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="120" w:line="264" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>
                  %s
                  %s
                  %s
                  %s
                  %s
                  %s
                  %s
                  %s
                  %s
                </w:styles>
                """.formatted(
                paragraphStyle("Normal", "Normal", 22, "000000", false, 0, 120),
                paragraphStyle("HTMLTitle", "HTML Title", 48, "0B2545", true, 0, 240),
                paragraphStyle("LessonHeading1", "Lesson Heading 1", 28, "000000", true, 0, 200),
                paragraphStyle("LessonHeading2", "Lesson Heading 2", 28, "000000", false, 0, 200),
                paragraphStyle("LessonHeading3", "Lesson Heading 3", 24, "000000", true, 160, 80),
                paragraphStyle("Heading1", "Heading 1", 32, "2E74B5", true, 320, 160),
                paragraphStyle("Heading2", "Heading 2", 26, "2E74B5", true, 240, 120),
                paragraphStyle("Heading3", "Heading 3", 24, "1F4D78", true, 160, 80),
                paragraphStyle("Caption", "Caption", 18, "68736E", false, 0, 120));
    }

    private String paragraphStyle(String id, String name, int halfPoints, String color, boolean bold, int before, int after) {
        return "<w:style w:type=\"paragraph\"" + ("Normal".equals(id) ? " w:default=\"true\"" : "") + " w:styleId=\"" + id + "\"><w:name w:val=\"" + name + "\"/>"
                + ("Normal".equals(id) ? "<w:qFormat/>" : "<w:basedOn w:val=\"Normal\"/><w:qFormat/>")
                + "<w:pPr><w:spacing w:before=\"" + before + "\" w:after=\"" + after + "\"/></w:pPr><w:rPr><w:rFonts w:ascii=\"" + FONT_FAMILY + "\" w:hAnsi=\"" + FONT_FAMILY + "\" w:eastAsia=\"" + FONT_FAMILY + "\"/><w:color w:val=\"" + color + "\"/><w:sz w:val=\"" + halfPoints + "\"/>" + (bold ? "<w:b/>" : "") + "</w:rPr></w:style>";
    }
}
