package com.lessonweb.lesson.storage;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class AssetStorageService {

    private static final Set<String> IMAGE_SUFFIXES = Set.of(
            ".png", ".jpg", ".jpeg", ".jp2", ".webp", ".gif", ".bmp", ".svg");

    private final LocalStorage storage;

    public AssetStorageService(LocalStorage storage) {
        this.storage = storage;
    }

    public Map<String, String> collectMineruAssets(String jobId, Path resultDir) {
        Path assetsDir = storage.jobDir(jobId).resolve("assets");
        try {
            Files.createDirectories(assetsDir);
            List<Path> entries;
            try (Stream<Path> paths = Files.walk(resultDir)) {
                entries = paths.filter(path -> !path.equals(resultDir)).sorted().toList();
            }
            Map<String, String> mapping = new HashMap<>();
            for (int index = 0; index < entries.size(); index++) {
                Path source = entries.get(index);
                if (!Files.isRegularFile(source) || !IMAGE_SUFFIXES.contains(extensionOf(source))) {
                    continue;
                }
                String safeName = String.format("%04d-%s", index + 1, source.getFileName());
                Files.copy(source, assetsDir.resolve(safeName),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                String url = "/api/assets/" + jobId + "/" + safeName;
                String relative = resultDir.relativize(source).toString().replace('\\', '/');
                mapping.put(relative, url);
                mapping.put(source.getFileName().toString(), url);
                int imagesIndex = relative.indexOf("images/");
                if (imagesIndex >= 0) {
                    mapping.put(relative.substring(imagesIndex), url);
                }
            }
            return Map.copyOf(mapping);
        } catch (IOException exception) {
            throw new IllegalStateException("MinerU 资源归档失败", exception);
        }
    }

    public Path getAsset(String jobId, String filename) {
        return storage.resolveAsset(jobId, filename);
    }

    public List<StoredAsset> describeAssets(String jobId) {
        Path assetsDir = storage.jobDir(jobId).resolve("assets");
        if (!Files.isDirectory(assetsDir)) return List.of();
        try (Stream<Path> paths = Files.list(assetsDir)) {
            return paths.filter(Files::isRegularFile).sorted().map(path -> describe(jobId, path)).toList();
        } catch (IOException exception) {
            throw new IllegalStateException("资源元数据读取失败", exception);
        }
    }

    private StoredAsset describe(String jobId, Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                input.transferTo(new java.io.OutputStream() {
                    @Override public void write(int value) { digest.update((byte) value); }
                    @Override public void write(byte[] value, int offset, int length) { digest.update(value, offset, length); }
                });
            }
            String name = path.getFileName().toString();
            return new StoredAsset("/api/assets/" + jobId + "/" + name, "assets/" + name,
                    Files.probeContentType(path), Files.size(path), HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("资源元数据读取失败", exception);
        }
    }

    public record StoredAsset(String src, String relativePath, String contentType, long sizeBytes, String sha256) {}

    private String extensionOf(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
    }
}
