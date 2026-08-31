package com.lessonweb.lesson.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lessonweb.lesson.adapter.MineruLessonDocumentAdapter;
import com.lessonweb.lesson.config.LessonProperties;
import com.lessonweb.lesson.exception.MineruException;
import com.lessonweb.lesson.model.job.JobStatus;
import com.lessonweb.lesson.model.job.ParseJob;
import com.lessonweb.lesson.model.job.ErrorDetail;
import com.lessonweb.lesson.parser.MineruDocumentParser;
import com.lessonweb.lesson.parser.MineruParseResult;
import com.lessonweb.lesson.storage.AssetStorageService;
import com.lessonweb.lesson.storage.LocalStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentParseServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void completesTaskAndPersistsProviderResultWithoutDocumentFileCache() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        LocalStorage storage = storage(objectMapper);
        ParseJobService jobs = jobService();
        MineruDocumentParser parser = mock(MineruDocumentParser.class);
        Path resultDir = tempDir.resolve("provider-result");
        Files.createDirectories(resultDir.resolve("images"));
        Files.write(resultDir.resolve("images/a.png"), new byte[]{1});
        MineruParseResult raw = new MineruParseResult(
                objectMapper.readTree("""
                        [{"type":"text","text_level":1,"text":"课程标题"},
                         {"type":"image","img_path":"images/a.png"}]
                        """),
                objectMapper.createArrayNode(), objectMapper.createObjectNode(),
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
        assertThat(storage.jobDir(completed.jobId()).resolve("lesson-document.json")).doesNotExist();
        assertThat(completed.document().blocks().get(1).toString()).contains("/api/assets/");
    }

    @Test
    void recordsProviderFailureWithoutLeakingExceptionToExecutor() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        LocalStorage storage = storage(objectMapper);
        ParseJobService jobs = jobService();
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

    @ParameterizedTest
    @CsvSource({"empty,DOCUMENT_CONTENT_EMPTY", "missing-image,DOCUMENT_CONTENT_INCOMPLETE",
            "unstructured-table,DOCUMENT_CONTENT_INCOMPLETE"})
    void neverPersistsIncompleteParsingAsSuccess(String scenario, String errorCode) throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        LocalStorage storage = storage(mapper);
        ParseJobService jobs = jobService();
        MineruDocumentParser parser = mock(MineruDocumentParser.class);
        Path resultDir = Files.createDirectories(tempDir.resolve("provider-result"));
        String json = switch (scenario) {
            case "empty" -> "[]";
            case "missing-image" -> "[{\"type\":\"text\",\"text\":\"Visible paragraph\"},{\"type\":\"image\",\"img_path\":\"missing.png\"}]";
            default -> "[{\"type\":\"table\",\"img_path\":\"table.png\"}]";
        };
        when(parser.parse(any(Path.class))).thenReturn(new MineruParseResult(mapper.readTree(json),
                mapper.createArrayNode(), mapper.createObjectNode(), resultDir));
        DocumentParseService service = service(storage, jobs, parser);
        var submission = service.createJob(new MockMultipartFile("file", "independent.pdf", "application/pdf", "%PDF-1.7".getBytes()));

        service.processJob(submission.job().jobId(), submission.sourcePath());

        var failed = service.getJob(submission.job().jobId());
        assertThat(failed.status()).isEqualTo(JobStatus.FAILED);
        assertThat(failed.error().code()).isEqualTo(errorCode);
        assertThat(failed.document()).isNull();
        org.mockito.Mockito.verify(jobs, org.mockito.Mockito.never()).success(anyString(), any(), anyList());
        assertThat(storage.jobDir(failed.jobId()).resolve("mineru-result.json")).isRegularFile();
    }

    @Test
    void rejectsNonPdfFilesAndInvalidPdfContent() {
        DocumentParseService service = service(storage(new ObjectMapper()), jobService(), mock(MineruDocumentParser.class));

        assertThatThrownBy(() -> service.createJob(new MockMultipartFile(
                "file", "lesson.png", "image/png", "image".getBytes())))
                .isInstanceOf(com.lessonweb.lesson.exception.AppException.class)
                .hasMessage("仅支持 PDF 格式文件");
        assertThatThrownBy(() -> service.createJob(new MockMultipartFile(
                "file", "lesson.pdf", "application/pdf", "not a PDF".getBytes())))
                .isInstanceOf(com.lessonweb.lesson.exception.AppException.class)
                .hasMessage("仅支持 PDF 格式文件");
    }

    @Test
    void rejectedSubmissionFailsJobAndRemovesOnlyItsUnprocessedUpload() throws Exception {
        LocalStorage storage = storage(new ObjectMapper());
        ParseJobService jobs = jobService();
        DocumentParseService service = new DocumentParseService(properties(), storage, jobs,
                mock(MineruDocumentParser.class), new AssetStorageService(storage), new MineruLessonDocumentAdapter(),
                task -> { throw new org.springframework.core.task.TaskRejectedException("full"); });
        assertThatThrownBy(() -> service.submit(new MockMultipartFile(
                "file", "lesson.pdf", "application/pdf", "%PDF-1.7".getBytes())))
                .isInstanceOf(com.lessonweb.lesson.exception.AppException.class)
                .satisfies(error -> assertThat(((com.lessonweb.lesson.exception.AppException) error).status().value()).isEqualTo(503));
        org.mockito.ArgumentCaptor<String> id = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jobs).fail(id.capture(), org.mockito.ArgumentMatchers.eq("PARSE_QUEUE_FULL"), anyString());
        assertThat(jobs.get(id.getValue()).status()).isEqualTo(JobStatus.FAILED);
        assertThat(storage.jobDir(id.getValue())).doesNotExist();
    }

    @Test
    void failedDatabaseInsertDoesNotLeaveAnUpload() throws Exception {
        LocalStorage storage = storage(new ObjectMapper());
        ParseJobService jobs = mock(ParseJobService.class);
        when(jobs.create(anyString(), anyString())).thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> service(storage, jobs, mock(MineruDocumentParser.class)).submit(new MockMultipartFile(
                "file", "lesson.pdf", "application/pdf", "%PDF-1.7".getBytes())))
                .isInstanceOf(IllegalStateException.class);
        try (var entries = Files.list(tempDir)) { assertThat(entries).isEmpty(); }
    }

    private DocumentParseService service(
            LocalStorage storage,
            ParseJobService jobs,
            MineruDocumentParser parser
    ) {
        return new DocumentParseService(
                properties(), storage, jobs, parser, new AssetStorageService(storage),
                new MineruLessonDocumentAdapter(), Runnable::run);
    }

    private LocalStorage storage(ObjectMapper objectMapper) {
        return new LocalStorage(properties(), objectMapper);
    }

    private ParseJobService jobService() {
        ParseJobService jobs = mock(ParseJobService.class);
        AtomicReference<ParseJob> state = new AtomicReference<>();
        when(jobs.create(anyString(), anyString())).thenAnswer(invocation -> {
            ParseJob job = new ParseJob(invocation.getArgument(0), JobStatus.PENDING,
                    invocation.getArgument(1), Instant.now(), null, null);
            state.set(job);
            return job;
        });
        when(jobs.processing(anyString())).thenAnswer(invocation -> {
            ParseJob current = state.get();
            ParseJob next = new ParseJob(current.jobId(), JobStatus.PROCESSING, current.sourceFileName(),
                    current.createdAt(), null, null);
            state.set(next);
            return next;
        });
        when(jobs.success(anyString(), any(), anyList())).thenAnswer(invocation -> {
            ParseJob current = state.get();
            ParseJob next = new ParseJob(current.jobId(), JobStatus.COMPLETED, current.sourceFileName(),
                    current.createdAt(), invocation.getArgument(1), null);
            state.set(next);
            return next;
        });
        when(jobs.fail(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            ParseJob current = state.get();
            ParseJob next = new ParseJob(current.jobId(), JobStatus.FAILED, current.sourceFileName(),
                    current.createdAt(), null, new ErrorDetail(invocation.getArgument(1), invocation.getArgument(2)));
            state.set(next);
            return next;
        });
        when(jobs.get(anyString())).thenAnswer(invocation -> state.get());
        return jobs;
    }

    private LessonProperties properties() {
        return new LessonProperties(
                "http://localhost:5173", tempDir.toString(), DataSize.ofMegabytes(200));
    }
}
