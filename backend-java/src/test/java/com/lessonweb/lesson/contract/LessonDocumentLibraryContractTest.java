package com.lessonweb.lesson.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lessonweb.lesson.LessonApplication;
import com.lessonweb.lesson.model.lesson.LessonDocument;
import com.lessonweb.lesson.model.lesson.LessonMetadata;
import com.lessonweb.lesson.model.lesson.FormulaBlock;
import com.lessonweb.lesson.model.lesson.ParagraphBlock;
import com.lessonweb.lesson.model.lesson.TableBlock;
import com.lessonweb.lesson.model.job.JobStatus;
import com.lessonweb.lesson.persistence.entity.DocumentContentEntity;
import com.lessonweb.lesson.persistence.mapper.ConversionMapper;
import com.lessonweb.lesson.persistence.mapper.DocumentContentMapper;
import com.lessonweb.lesson.service.LessonDocumentLibraryService;
import com.lessonweb.lesson.service.ParseJobService;
import com.lessonweb.lesson.storage.AssetStorageService.StoredAsset;
import com.alibaba.druid.pool.DruidDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;
import javax.sql.DataSource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = LessonApplication.class)
@AutoConfigureMockMvc
class LessonDocumentLibraryContractTest extends MySqlContractTest {
    @Autowired MockMvc mockMvc;
    @Autowired ParseJobService jobs;
    @Autowired ObjectMapper objectMapper;
    @Autowired DataSource dataSource;
    @Autowired ConversionMapper conversions;
    @Autowired DocumentContentMapper contents;
    @Autowired LessonDocumentLibraryService library;

    @Test
    void usesConfiguredDruidPool() {
        org.assertj.core.api.Assertions.assertThat(dataSource).isInstanceOf(DruidDataSource.class);
        DruidDataSource druid = (DruidDataSource) dataSource;
        org.assertj.core.api.Assertions.assertThat(druid.getInitialSize()).isEqualTo(5);
        org.assertj.core.api.Assertions.assertThat(druid.getMinIdle()).isEqualTo(5);
        org.assertj.core.api.Assertions.assertThat(druid.getMaxActive()).isEqualTo(20);
        org.assertj.core.api.Assertions.assertThat(druid.getMaxWait()).isEqualTo(60000);
        org.assertj.core.api.Assertions.assertThat(druid.getValidationQuery()).isEqualTo("SELECT 1 FROM DUAL");
    }

    @Test
    void listsReadsUpdatesAndSoftDeletesDocument() throws Exception {
        LessonDocument document = completedDocument("初始标题");

        mockMvc.perform(get("/api/lesson-documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].title".formatted(document.documentId())).value("初始标题"));
        mockMvc.perform(get("/api/lesson-documents/{id}", document.documentId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocks[0].text").value("中文正文"))
                .andExpect(jsonPath("$.blocks[1].html").value("<table><tr><td>表格</td></tr></table>"))
                .andExpect(jsonPath("$.blocks[2].latex").value("x^2+y^2=z^2"));

        LessonDocument edited = new LessonDocument("1.0", document.documentId(), "编辑标题",
                document.metadata(), List.of(new ParagraphBlock("block-1", "编辑正文")));
        mockMvc.perform(put("/api/lesson-documents/{id}", document.documentId())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(edited)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("编辑标题"));

        mockMvc.perform(delete("/api/lesson-documents/{id}", document.documentId()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/lesson-documents/{id}", document.documentId()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("DOCUMENT_NOT_FOUND"));
        mockMvc.perform(get("/api/documents/{id}", document.documentId()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"));
        mockMvc.perform(get("/api/assets/{id}/image.png", document.documentId()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("DOCUMENT_NOT_FOUND"));
        mockMvc.perform(put("/api/lesson-documents/{id}", document.documentId())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(edited)))
                .andExpect(status().isNotFound());
        org.assertj.core.api.Assertions.assertThat(conversions.selectById(document.documentId()).getDelFlag()).isEqualTo("2");
        org.assertj.core.api.Assertions.assertThat(contents.selectById(document.documentId())).isNotNull();
        org.assertj.core.api.Assertions.assertThat(library.list()).noneMatch(item -> item.id().equals(document.documentId()));
    }

    @Test
    void rejectsMismatchedDocumentId() throws Exception {
        LessonDocument document = completedDocument("标题");
        LessonDocument mismatched = new LessonDocument("1.0", UUID.randomUUID().toString(), "标题",
                document.metadata(), document.blocks());
        mockMvc.perform(put("/api/lesson-documents/{id}", document.documentId())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(mismatched)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("DOCUMENT_ID_MISMATCH"));
    }

    @Test
    void rejectsInvalidV1AndUnknownDocument() throws Exception {
        LessonDocument document = completedDocument("标题");
        String invalid = objectMapper.writeValueAsString(document).replace("\"version\":\"1.0\"", "\"version\":\"2.0\"");
        mockMvc.perform(put("/api/lesson-documents/{id}", document.documentId())
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/lesson-documents/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    void marksPendingAndProcessingJobsFailedAfterRestart() {
        String pendingId = UUID.randomUUID().toString();
        String processingId = UUID.randomUUID().toString();
        jobs.create(pendingId, "待处理.pdf");
        jobs.create(processingId, "处理中.pdf");
        jobs.processing(processingId);
        org.assertj.core.api.Assertions.assertThat(conversions.selectById(pendingId).getStatus()).isEqualTo(0);
        org.assertj.core.api.Assertions.assertThat(conversions.selectById(processingId).getStatus()).isEqualTo(1);

        jobs.recoverInterruptedJobs();

        org.assertj.core.api.Assertions.assertThat(jobs.get(pendingId).status()).isEqualTo(JobStatus.FAILED);
        org.assertj.core.api.Assertions.assertThat(jobs.get(processingId).error().code()).isEqualTo("SERVICE_RESTARTED");
    }

    @Test
    void sortsLibraryByUpdatedAtDescending() {
        LessonDocument older = completedDocument("较早更新");
        LessonDocument newer = completedDocument("较晚更新");
        DocumentContentEntity olderEntity = new DocumentContentEntity();
        olderEntity.setId(older.documentId());
        olderEntity.setUpdatedAt(LocalDateTime.of(2100, 1, 1, 0, 0));
        contents.updateById(olderEntity);
        DocumentContentEntity newerEntity = new DocumentContentEntity();
        newerEntity.setId(newer.documentId());
        newerEntity.setUpdatedAt(LocalDateTime.of(2100, 1, 2, 0, 0));
        contents.updateById(newerEntity);

        org.assertj.core.api.Assertions.assertThat(library.list().subList(0, 2))
                .extracting(item -> item.id()).containsExactly(newer.documentId(), older.documentId());
    }

    private LessonDocument completedDocument(String title) {
        String id = UUID.randomUUID().toString();
        LessonDocument document = new LessonDocument("1.0", id, title,
                new LessonMetadata("pdf", "课程.pdf"), List.of(
                        new ParagraphBlock("block-1", "中文正文"),
                        new TableBlock("block-2", "<table><tr><td>表格</td></tr></table>"),
                        new FormulaBlock("block-3", "x^2+y^2=z^2")));
        jobs.create(id, "课程.pdf");
        jobs.processing(id);
        jobs.success(id, document, List.of(new StoredAsset(
                "/api/assets/" + id + "/image.png", "assets/image.png", "image/png", 4,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")));
        return document;
    }
}
