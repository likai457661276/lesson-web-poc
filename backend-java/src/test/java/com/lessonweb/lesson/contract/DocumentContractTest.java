package com.lessonweb.lesson.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lessonweb.lesson.LessonApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = LessonApplication.class)
@AutoConfigureMockMvc
class DocumentContractTest extends MySqlContractTest {

    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void parseAndGetUsePythonFieldNamesAndStatuses() throws Exception {
        var file = new MockMultipartFile("file", "课程.pdf", "application/pdf", "%PDF-1.7 fake".getBytes());
        String body = mockMvc.perform(multipart("/api/documents/parse").file(file))
                .andExpect(status().isAccepted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.jobId", matchesPattern("[a-f0-9-]{36}")))
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.sourceFileName").value("课程.pdf"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.document").isEmpty())
                .andExpect(jsonPath("$.error").isEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode payload = objectMapper.readTree(body);
        mockMvc.perform(get("/api/documents/{jobId}", payload.get("jobId").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceFileName").value("课程.pdf"));
    }

    @Test
    void unsupportedUploadMatchesApplicationErrorContract() throws Exception {
        var file = new MockMultipartFile("file", "课程.png", "image/png", "fake".getBytes());
        mockMvc.perform(multipart("/api/documents/parse").file(file))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_FILE"))
                .andExpect(jsonPath("$.error.message").value("仅支持 PDF 格式文件"));
    }

    @Test
    void unknownJobMatchesApplicationErrorContract() throws Exception {
        mockMvc.perform(get("/api/documents/{jobId}", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"));
    }

    @Test
    void exportEndpointExposesDownloadContract() throws Exception {
        mockMvc.perform(post("/api/documents/export-docx")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"html\":\"<h1>课程</h1>\",\"filename\":\"课程.docx\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(DOCX_CONTENT_TYPE))
                .andExpect(header().string("Content-Disposition", matchesPattern(
                        "attachment; filename=\\\"lesson.docx\\\"; filename\\*=UTF-8''.+")));
    }

    @Test
    void invalidExportBodyUsesFastApiCompatibleStatus() throws Exception {
        mockMvc.perform(post("/api/documents/export-docx")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"html\":\"\",\"filename\":\"lesson.docx\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail[0].loc[0]").value("body"));
    }

    @Test
    void exportFilenameDefaultsButExplicitNullIsRejected() throws Exception {
        mockMvc.perform(post("/api/documents/export-docx")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"html\":\"<p>课程</p>\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"lesson.docx\"; filename*=UTF-8''lesson.docx"));

        mockMvc.perform(post("/api/documents/export-docx")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"html\":\"<p>课程</p>\",\"filename\":null}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void missingMultipartFileReturns422() throws Exception {
        mockMvc.perform(multipart("/api/documents/parse"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail[0].loc[1]").value("file"));
    }

    @Test
    void corsAllowsBothLocalFrontendOrigins() throws Exception {
        for (String origin : new String[]{"http://localhost:5173", "http://127.0.0.1:5173"}) {
            mockMvc.perform(options("/api/documents/parse")
                            .header("Origin", origin)
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin));
        }
    }
}
