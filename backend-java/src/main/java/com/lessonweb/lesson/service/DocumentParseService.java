package com.lessonweb.lesson.service;

import com.lessonweb.lesson.adapter.MineruLessonDocumentAdapter;
import com.lessonweb.lesson.config.LessonProperties;
import com.lessonweb.lesson.exception.AppException;
import com.lessonweb.lesson.model.job.ParseJob;
import com.lessonweb.lesson.model.lesson.LessonDocument;
import com.lessonweb.lesson.parser.MineruDocumentParser;
import com.lessonweb.lesson.parser.MineruParseResult;
import com.lessonweb.lesson.storage.AssetStorageService;
import com.lessonweb.lesson.storage.LocalStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentParseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentParseService.class);
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private final LessonProperties properties;
    private final LocalStorage storage;
    private final ParseJobService jobs;
    private final MineruDocumentParser parser;
    private final AssetStorageService assets;
    private final MineruLessonDocumentAdapter adapter;

    public DocumentParseService(
            LessonProperties properties,
            LocalStorage storage,
            ParseJobService jobs,
            MineruDocumentParser parser,
            AssetStorageService assets,
            MineruLessonDocumentAdapter adapter
    ) {
        this.properties = properties;
        this.storage = storage;
        this.jobs = jobs;
        this.parser = parser;
        this.assets = assets;
        this.adapter = adapter;
    }

    public Submission createJob(MultipartFile upload) {
        String filename = safeFilename(upload.getOriginalFilename());
        if (filename.isBlank() || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf") || !hasPdfSignature(upload)) {
            throw new AppException("UNSUPPORTED_FILE", "仅支持 PDF 格式文件", HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
        String jobId = UUID.randomUUID().toString();
        Path sourcePath = storage.saveUpload(jobId, filename, upload);
        return new Submission(jobs.create(jobId, filename), sourcePath);
    }

    public void processJob(String jobId, Path sourcePath) {
        ParseJob job = jobs.processing(jobId);
        Instant startedAt = Instant.now();
        try {
            MineruParseResult raw = parser.parse(sourcePath);
            storage.saveProviderResult(jobId, raw);
            Map<String, String> assetUrls = assets.collectMineruAssets(jobId, raw.resultDir());
            LessonDocument document = adapter.convert(raw, jobId, job.sourceFileName(), assetUrls);
            storage.saveDocument(jobId, document);
            jobs.success(jobId, document);
            LOGGER.info("document_parse_completed jobId={} fileName={} durationMs={} blockCount={}",
                    jobId, job.sourceFileName(), Duration.between(startedAt, Instant.now()).toMillis(),
                    document.blocks().size());
        } catch (Exception exception) {
            String code;
            String message;
            if (exception instanceof AppException appException) {
                code = appException.code();
                message = appException.getMessage();
            } else {
                code = "ADAPTER_CONVERT_FAILED";
                message = "文档转换失败";
            }
            jobs.fail(jobId, code, message);
            LOGGER.error("document_parse_failed jobId={} fileName={} errorCode={}",
                    jobId, job.sourceFileName(), code, exception);
        }
    }

    public ParseJob getJob(String jobId) {
        return jobs.get(jobId);
    }

    private String safeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "";
        }
        String normalized = originalFilename.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash < 0 ? normalized : normalized.substring(slash + 1);
    }

    private boolean hasPdfSignature(MultipartFile upload) {
        try (var input = upload.getInputStream()) {
            return java.util.Arrays.equals(input.readNBytes(PDF_SIGNATURE.length), PDF_SIGNATURE);
        } catch (java.io.IOException exception) {
            return false;
        }
    }

    public record Submission(ParseJob job, Path sourcePath) {
    }
}
