package com.lessonweb.lesson.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lessonweb.lesson.config.LessonProperties;
import com.lessonweb.lesson.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalAndAssetStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void savesUploadEnforcesSizeAndBlocksAssetTraversal() throws Exception {
        LocalStorage storage = storage(4);
        String jobId = UUID.randomUUID().toString();
        Path source = storage.saveUpload(jobId, "lesson.pdf",
                new MockMultipartFile("file", "lesson.pdf", "application/pdf", "1234".getBytes()));
        assertThat(source.getFileName().toString()).isEqualTo("source.pdf");
        assertThat(Files.readString(source)).isEqualTo("1234");

        String oversizedJob = UUID.randomUUID().toString();
        assertThatThrownBy(() -> storage.saveUpload(oversizedJob, "lesson.pdf",
                new MockMultipartFile("file", "lesson.pdf", "application/pdf", "12345".getBytes())))
                .isInstanceOfSatisfying(AppException.class, error -> {
                    assertThat(error.code()).isEqualTo("FILE_TOO_LARGE");
                    assertThat(error.status().value()).isEqualTo(413);
                });
        assertThat(storage.jobDir(oversizedJob)).doesNotExist();

        assertThatThrownBy(() -> storage.resolveAsset(jobId, "../source.pdf"))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.code()).isEqualTo("ASSET_NOT_FOUND"));
    }

    @Test
    void archivesSupportedImagesAndBuildsAllProviderLookupKeys() throws Exception {
        LocalStorage storage = storage(1024);
        String jobId = UUID.randomUUID().toString();
        Files.createDirectory(storage.jobDir(jobId));
        Path resultDir = storage.jobDir(jobId).resolve("mineru");
        Files.createDirectories(resultDir.resolve("nested/images"));
        Files.write(resultDir.resolve("nested/images/a.png"), new byte[]{1});
        Files.writeString(resultDir.resolve("nested/ignored.txt"), "ignored");

        Map<String, String> urls = new AssetStorageService(storage).collectMineruAssets(jobId, resultDir);

        assertThat(urls).containsKeys("nested/images/a.png", "images/a.png", "a.png");
        Path asset = storage.resolveAsset(jobId, Path.of(urls.get("a.png")).getFileName().toString());
        assertThat(Files.readAllBytes(asset)).containsExactly(1);
    }

    private LocalStorage storage(long maxBytes) {
        LessonProperties properties = new LessonProperties(
                "http://localhost:5173", tempDir.toString(), DataSize.ofBytes(maxBytes));
        return new LocalStorage(properties, new ObjectMapper().findAndRegisterModules());
    }
}
