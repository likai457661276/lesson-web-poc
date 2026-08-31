package com.lessonweb.lesson.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lessonweb.lesson.exception.MineruException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MineruResultExtractorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MineruResultExtractor extractor = new MineruResultExtractor(objectMapper);

    @TempDir
    Path tempDir;

    @Test
    void extractsContentAssetsAndOnlyLightweightOcrBoxes() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("nested/document_content_list.json", "[{\"type\":\"text\",\"text\":\"正文\"}]".getBytes());
        entries.put("nested/document_model.json", """
                [[{"type":"paragraph_title","bbox":[0,0,1,1]},
                  {"type":"ocr_text","bbox":[0.1,0.1,0.2,0.2],"text":"ignored"}]]
                """.getBytes());
        entries.put("nested/images/a.png", new byte[]{1, 2, 3});
        entries.put("nested/layout.json", "{\"pdf_info\":[{\"page_idx\":0,\"page_size\":[600,800]}]}".getBytes());

        MineruParseResult result = extractor.extract(zip(entries),
                tempDir.resolve("result.zip"), tempDir.resolve("mineru"));

        assertThat(result.contentList()).hasSize(1);
        assertThat(result.layout().path("pdf_info").get(0).path("page_size").get(0).asInt()).isEqualTo(600);
        assertThat(result.ocrLayout().toString()).isEqualTo("[[{\"bbox\":[0.1,0.1,0.2,0.2]}]]");
        assertThat(Files.readAllBytes(tempDir.resolve("mineru/nested/images/a.png")))
                .containsExactly(1, 2, 3);
    }

    @Test
    void rejectsZipSlipAndMissingContentList() throws Exception {
        assertThatThrownBy(() -> extractor.extract(zip(Map.of("../outside.txt", "bad".getBytes())),
                tempDir.resolve("slip.zip"), tempDir.resolve("slip")))
                .isInstanceOf(MineruException.class)
                .hasMessage("MinerU 压缩包包含非法路径");

        assertThatThrownBy(() -> extractor.extract(zip(Map.of("readme.txt", "ok".getBytes())),
                tempDir.resolve("missing.zip"), tempDir.resolve("missing")))
                .isInstanceOf(MineruException.class)
                .hasMessage("MinerU 结果中缺少 content_list.json");
    }

    private byte[] zip(Map<String, byte[]> entries) throws IOException {
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
}
