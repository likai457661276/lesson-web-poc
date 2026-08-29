package com.lessonweb.lesson.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lessonweb.lesson.adapter.MineruLessonDocumentAdapter;
import com.lessonweb.lesson.config.LessonProperties;
import com.lessonweb.lesson.exception.MineruException;
import com.lessonweb.lesson.model.job.JobStatus;
import com.lessonweb.lesson.parser.MineruDocumentParser;
import com.lessonweb.lesson.parser.MineruParseResult;
import com.lessonweb.lesson.storage.AssetStorageService;
import com.lessonweb.lesson.storage.LocalStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentParseServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void completesTaskAndPersistsProviderAndLessonDocuments() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        LocalStorage storage = storage(objectMapper);
        ParseJobService jobs = new ParseJobService();
        MineruDocumentParser parser = mock(MineruDocumentParser.class);
        Path resultDir = tempDir.resolve("provider-result");
        Files.createDirectories(resultDir.resolve("images"));
        Files.write(resultDir.resolve("images/a.png"), new byte[]{1});
        MineruParseResult raw = new MineruParseResult(
                objectMapper.readTree("""
                        [{"type":"text","text_level":1,"text":"课程标题"},
                         {"type":"image","img_path":"images/a.png"}]
                        """),
                objectMapper.createArrayNode(),
                resultDir);
        when(parser.parse(any(Path.class))).thenReturn(raw);
        DocumentParseService service = service(storage, jobs, parser);

        DocumentParseService.Submission submission = service.createJob(new MockMultipartFile(
                "file", "lesson.pdf", "application/pdf", "%PDF-1.7".getBytes()));
        assertThat(submission.job().status()).isEqualTo(JobStatus.PENDING);
        service.processJob(submission.job().jobId(), submission.sourcePath());

        var completed = service.getJob(submission.job().jobId());
        assertThat(completed.status()).isEqualTo(JobStatus.COMPLETED);
        assertThat(completed.document().title()).isEqualTo("课程标题");
        assertThat(completed.document().blocks()).extracting(block -> block.type())
                .containsExactly("heading", "image");
        assertThat(storage.jobDir(completed.jobId()).resolve("mineru-result.json")).isRegularFile();
        assertThat(storage.jobDir(completed.jobId()).resolve("lesson-document.json")).isRegularFile();
        assertThat(completed.document().blocks().get(1).toString()).contains("/api/assets/");
    }

    @Test
    void recordsProviderFailureWithoutLeakingExceptionToExecutor() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        LocalStorage storage = storage(objectMapper);
        ParseJobService jobs = new ParseJobService();
        MineruDocumentParser parser = mock(MineruDocumentParser.class);
        when(parser.parse(any(Path.class))).thenThrow(new MineruException("上游失败"));
        DocumentParseService service = service(storage, jobs, parser);

        DocumentParseService.Submission submission = service.createJob(new MockMultipartFile(
                "file", "lesson.pdf", "application/pdf", "%PDF-1.7".getBytes()));
        service.processJob(submission.job().jobId(), submission.sourcePath());

        var failed = service.getJob(submission.job().jobId());
        assertThat(failed.status()).isEqualTo(JobStatus.FAILED);
        assertThat(failed.error().code()).isEqualTo("MINERU_PARSE_FAILED");
        assertThat(failed.error().message()).isEqualTo("上游失败");
    }

    @Test
    void rejectsNonPdfFilesAndInvalidPdfContent() {
        DocumentParseService service = service(storage(new ObjectMapper()), new ParseJobService(), mock(MineruDocumentParser.class));

        assertThatThrownBy(() -> service.createJob(new MockMultipartFile(
                "file", "lesson.png", "image/png", "image".getBytes())))
                .isInstanceOf(com.lessonweb.lesson.exception.AppException.class)
                .hasMessage("仅支持 PDF 格式文件");
        assertThatThrownBy(() -> service.createJob(new MockMultipartFile(
                "file", "lesson.pdf", "application/pdf", "not a PDF".getBytes())))
                .isInstanceOf(com.lessonweb.lesson.exception.AppException.class)
                .hasMessage("仅支持 PDF 格式文件");
    }

    private DocumentParseService service(
            LocalStorage storage,
            ParseJobService jobs,
            MineruDocumentParser parser
    ) {
        return new DocumentParseService(
                properties(), storage, jobs, parser, new AssetStorageService(storage),
                new MineruLessonDocumentAdapter());
    }

    private LocalStorage storage(ObjectMapper objectMapper) {
        return new LocalStorage(properties(), objectMapper);
    }

    private LessonProperties properties() {
        return new LessonProperties(
                "http://localhost:5173", tempDir.toString(), DataSize.ofMegabytes(200));
    }
}
