package com.lessonweb.lesson.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lessonweb.lesson.model.lesson.LessonDocument;
import com.lessonweb.lesson.adapter.MineruLessonDocumentAdapter;
import com.lessonweb.lesson.parser.MineruParseResult;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class LessonDocumentContractTest extends MySqlContractTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void allSixBlockTypesAndCamelCaseFieldsDeserialize() throws Exception {
        String json = """
                {
                  "version": "1.0",
                  "documentId": "lesson-1",
                  "title": "勾股定理",
                  "metadata": {"sourceType": "pdf", "sourceFileName": "lesson.pdf"},
                  "blocks": [
                    {"id":"1","type":"heading","level":1,"text":"目标","alignment":"left"},
                    {"id":"2","type":"paragraph","text":"正文","reviewNote":"第 2 页需复核"},
                    {"id":"3","type":"list","items":["A"],"ordered":false},
                    {"id":"4","type":"table","html":"<table></table>"},
                    {"id":"5","type":"image","src":"/api/assets/a/1.png","alt":null},
                    {"id":"6","type":"formula","latex":"a^2+b^2=c^2"}
                  ]
                }
                """;

        LessonDocument document = objectMapper.readValue(json, LessonDocument.class);
        assertThat(document.documentId()).isEqualTo("lesson-1");
        assertThat(document.metadata().sourceFileName()).isEqualTo("lesson.pdf");
        assertThat(document.blocks()).hasSize(6);
        assertThat(objectMapper.readTree(objectMapper.writeValueAsString(document))
                .get("blocks").get(1).get("reviewNote").asText()).isEqualTo("第 2 页需复核");
        assertThat(objectMapper.readTree(objectMapper.writeValueAsString(document))
                .get("blocks").get(0).get("type").asText()).isEqualTo("heading");
    }

    @Test
    void unknownProviderBlockTypeIsRejected() {
        String json = """
                {"documentId":"lesson-1","title":"test",
                 "metadata":{"sourceType":"pdf","sourceFileName":"test.pdf"},
                 "blocks":[{"id":"1","type":"mineru_text","text":"raw"}]}
                """;

        assertThatThrownBy(() -> objectMapper.readValue(json, LessonDocument.class))
                .hasMessageContaining("mineru_text");
    }

    @Test
    void unknownSourceTextBecomesReviewedV1ParagraphWithoutProviderFields() throws Exception {
        var raw = new MineruParseResult(objectMapper.readTree("""
                [{"type":"unrecognized","text":"Independent text","page_idx":2,"bbox":[100,100,400,200]}]
                """), objectMapper.createArrayNode(), objectMapper.createObjectNode(), Path.of("unused"));
        var document = new MineruLessonDocumentAdapter().convert(raw, "review", "independent.pdf", Map.of());
        String json = objectMapper.writeValueAsString(document);
        assertThat(objectMapper.readValue(json, LessonDocument.class)).isEqualTo(document);
        var block = objectMapper.readTree(json).path("blocks").get(0);
        assertThat(block.path("type").asText()).isEqualTo("paragraph");
        assertThat(block.path("text").asText()).isEqualTo("Independent text");
        assertThat(block.path("reviewNote").asText()).contains("第 3 页", "未识别内容类型");
        assertThat(json).doesNotContain("page_idx", "bbox", "unrecognized");
    }
}
