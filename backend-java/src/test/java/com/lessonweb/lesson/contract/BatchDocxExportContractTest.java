package com.lessonweb.lesson.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lessonweb.lesson.controller.DocumentController;
import com.lessonweb.lesson.docx.BatchDocxExportService;
import com.lessonweb.lesson.docx.DocxFontService;
import com.lessonweb.lesson.docx.DocxFormulaRenderer;
import com.lessonweb.lesson.docx.DocxImageRenderer;
import com.lessonweb.lesson.docx.HtmlToDocxService;
import com.lessonweb.lesson.docx.formula.LatexToMathmlConverter;
import com.lessonweb.lesson.docx.formula.MathmlToOmmlConverter;
import com.lessonweb.lesson.exception.GlobalExceptionHandler;
import com.lessonweb.lesson.service.DocumentParseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BatchDocxExportContractTest {
    private static final String ENDPOINT = "/api/documents/export-docx-batch";
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var formulas = new DocxFormulaRenderer(new LatexToMathmlConverter(), new MathmlToOmmlConverter());
        var docx = new HtmlToDocxService(formulas, new DocxImageRenderer(), new DocxFontService());
        mvc = MockMvcBuilders.standaloneSetup(new DocumentController(
                        mock(DocumentParseService.class), docx, new BatchDocxExportService(docx)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void exportsDistinctDocxFilesWithSafeUniqueNamesAndPreservesOrder() throws Exception {
        byte[] archive = mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("""
                {"documents":[
                  {"html":"<h1>第一篇</h1>","filename":"../课程.docx"},
                  {"html":"<p>第二篇</p>","filename":"课程.docx"},
                  {"html":"<p>第三篇</p>","filename":"课程 (2).docx"}
                ]}
                """))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"lesson-documents.zip\""))
                .andReturn().getResponse().getContentAsByteArray();
        Map<String, byte[]> files = unzip(archive);
        assertThat(files.keySet()).containsExactly("课程.docx", "课程 (2).docx", "课程 (2) (2).docx");
        String[] contents = {"第一篇", "第二篇", "第三篇"};
        int index = 0;
        for (byte[] file : files.values()) {
            Map<String, byte[]> parts = unzip(file);
            assertThat(parts).containsKeys("[Content_Types].xml", "word/document.xml");
            assertThat(new String(parts.get("word/document.xml"), StandardCharsets.UTF_8)).contains(contents[index++]);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"documents\":null}", "{\"documents\":[]}", "{\"documents\":[null]}",
            "{\"documents\":[{}]}", "{\"documents\":[{\"html\":\"\"}]}",
            "{\"documents\":[{\"html\":\"<p>ok</p>\",\"filename\":null}]}"
    })
    void rejectsInvalidBatchAndNestedDocuments(String body) throws Exception {
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail[0].loc[0]").value("body"));
    }

    @Test
    void rejectsMoreThanTwentyDocuments() throws Exception {
        String body = new ObjectMapper().writeValueAsString(Map.of("documents",
                Collections.nCopies(21, Map.of("html", "<p>text</p>"))));
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rejectsExcessiveCombinedHtmlBeforeRendering() throws Exception {
        String body = new ObjectMapper().writeValueAsString(Map.of("documents",
                Collections.nCopies(2, Map.of("html", "x".repeat(12_500_001)))));
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error.code").value("BATCH_EXPORT_TOO_LARGE"));
    }

    @Test
    void failsEntireBatchInsteadOfReturningPartialArchive() throws Exception {
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("""
                {"documents":[{"html":"<p>有效文档</p>"},{"html":"<script>bad</script>"}]}
                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist("Content-Disposition"))
                .andExpect(jsonPath("$.error.code").value("EMPTY_HTML"));
    }

    private Map<String, byte[]> unzip(byte[] data) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(data), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) entries.put(entry.getName(), zip.readAllBytes());
        }
        return entries;
    }
}
