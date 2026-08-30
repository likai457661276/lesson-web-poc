package com.lessonweb.lesson.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lessonweb.lesson.config.LessonProperties;
import com.lessonweb.lesson.exception.AppException;
import com.lessonweb.lesson.parser.MineruParseResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

@Component
public class LocalStorage {

    private static final Pattern JOB_ID_PATTERN = Pattern.compile("[a-f0-9-]{36}");
    private static final int BUFFER_SIZE = 1024 * 1024;

    private final Path root;
    private final long maxBytes;
    private final ObjectMapper objectMapper;

    public LocalStorage(LessonProperties properties, ObjectMapper objectMapper) {
        this.root = Path.of(properties.dataDir()).toAbsolutePath().normalize();
        this.maxBytes = properties.maxFileSize().toBytes();
        this.objectMapper = objectMapper;
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建任务数据目录", exception);
        }
    }

    public Path jobDir(String jobId) {
        if (jobId == null || !JOB_ID_PATTERN.matcher(jobId).matches()) {
            throw assetNotFound();
        }
        Path path = root.resolve(jobId).normalize();
        if (!path.startsWith(root)) {
            throw assetNotFound();
        }
        return path;
    }

    public Path saveUpload(String jobId, String filename, MultipartFile upload) {
        Path sourceDir = jobDir(jobId);
        String extension = extensionOf(filename);
        Path target = sourceDir.resolve("source" + extension);
        try {
            Files.createDirectory(sourceDir);
            long written = 0;
            byte[] buffer = new byte[BUFFER_SIZE];
            try (InputStream input = upload.getInputStream(); OutputStream output = Files.newOutputStream(target)) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    written += count;
                    if (written > maxBytes) {
                        throw new AppException("FILE_TOO_LARGE", "文件大小超过限制", HttpStatus.PAYLOAD_TOO_LARGE);
                    }
                    output.write(buffer, 0, count);
                }
            }
            return target;
        } catch (AppException exception) {
            deleteFailedUpload(sourceDir, target);
            throw exception;
        } catch (IOException exception) {
            deleteFailedUpload(sourceDir, target);
            throw new AppException("UPLOAD_SAVE_FAILED", "上传文件保存失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void saveProviderResult(String jobId, MineruParseResult result) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("content_list", result.contentList());
        payload.set("ocr_layout", result.ocrLayout());
        writeJson(jobDir(jobId).resolve("mineru-result.json"), payload);
    }

    public Path resolveAsset(String jobId, String filename) {
        Path assetsDir = jobDir(jobId).resolve("assets").normalize();
        Path path = assetsDir.resolve(filename == null ? "" : filename).normalize();
        if (!path.startsWith(assetsDir) || !Files.isRegularFile(path)) {
            throw assetNotFound();
        }
        return path;
    }

    private void writeJson(Path path, Object value) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
        } catch (IOException exception) {
            throw new AppException("STORAGE_WRITE_FAILED", "任务结果保存失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot).toLowerCase();
    }

    private void deleteFailedUpload(Path sourceDir, Path target) {
        try {
            Files.deleteIfExists(target);
            Files.deleteIfExists(sourceDir);
        } catch (IOException ignored) {
            // The original upload error is more useful to the API caller.
        }
    }

    private AppException assetNotFound() {
        return new AppException("ASSET_NOT_FOUND", "资源不存在", HttpStatus.NOT_FOUND);
    }
}
