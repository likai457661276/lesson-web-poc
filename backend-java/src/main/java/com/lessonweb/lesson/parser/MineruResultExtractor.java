package com.lessonweb.lesson.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lessonweb.lesson.exception.MineruException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

@Component
public class MineruResultExtractor {

    private final ObjectMapper objectMapper;

    public MineruResultExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MineruParseResult extract(byte[] archive, Path archivePath, Path resultDir) {
        try {
            Files.createDirectories(resultDir);
            Files.write(archivePath, archive, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            extractArchive(archive, resultDir);
            JsonNode contentList = loadRequiredContentList(resultDir);
            JsonNode ocrLayout = loadOcrLayout(resultDir);
            return new MineruParseResult(contentList, ocrLayout, loadLayout(resultDir), resultDir);
        } catch (MineruException exception) {
            throw exception;
        } catch (ZipException exception) {
            throw new MineruException("MinerU 结果压缩包无效", exception);
        } catch (IOException exception) {
            throw new MineruException("MinerU 结果压缩包读取失败", exception);
        }
    }

    private void extractArchive(byte[] archive, Path resultDir) throws IOException {
        Path root = resultDir.toAbsolutePath().normalize();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            boolean hasEntry = false;
            while ((entry = input.getNextEntry()) != null) {
                hasEntry = true;
                String portableName = entry.getName().replace('\\', '/');
                Path target = root.resolve(portableName).normalize();
                if (!target.startsWith(root)) {
                    throw new MineruException("MinerU 压缩包包含非法路径");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                input.closeEntry();
            }
            if (!hasEntry) {
                throw new MineruException("MinerU 结果压缩包无效");
            }
        }
    }

    private JsonNode loadRequiredContentList(Path resultDir) {
        Path contentFile = findFirst(resultDir, true);
        if (contentFile == null) {
            contentFile = findFirst(resultDir, false);
        }
        if (contentFile == null) {
            throw new MineruException("MinerU 结果中缺少 content_list.json");
        }
        try {
            JsonNode content = objectMapper.readTree(contentFile.toFile());
            if (!content.isArray()) {
                throw new MineruException("MinerU content_list.json 格式无效");
            }
            return content;
        } catch (MineruException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new MineruException("MinerU content_list.json 读取失败", exception);
        }
    }

    private Path findFirst(Path resultDir, boolean suffixed) {
        try (Stream<Path> paths = Files.walk(resultDir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> suffixed
                            ? path.getFileName().toString().endsWith("_content_list.json")
                            : path.getFileName().toString().equals("content_list.json"))
                    .sorted()
                    .findFirst()
                    .orElse(null);
        } catch (IOException exception) {
            throw new MineruException("MinerU content_list.json 读取失败", exception);
        }
    }

    private JsonNode loadLayout(Path resultDir) throws IOException {
        try (Stream<Path> paths = Files.walk(resultDir)) {
            Path file = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("layout.json"))
                    .sorted().findFirst().orElse(null);
            return file == null ? objectMapper.createObjectNode() : objectMapper.readTree(file.toFile());
        }
    }

    public JsonNode loadOcrLayout(Path resultDir) {
        Path modelFile;
        try (Stream<Path> paths = Files.walk(resultDir)) {
            modelFile = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith("_model.json"))
                    .sorted()
                    .findFirst()
                    .orElse(null);
        } catch (IOException exception) {
            return objectMapper.createArrayNode();
        }
        if (modelFile == null) {
            return objectMapper.createArrayNode();
        }
        try {
            JsonNode model = objectMapper.readTree(modelFile.toFile());
            if (!model.isArray()) {
                return objectMapper.createArrayNode();
            }
            ArrayNode pages = objectMapper.createArrayNode();
            for (JsonNode page : model) {
                ArrayNode boxes = objectMapper.createArrayNode();
                if (page.isArray()) {
                    for (JsonNode item : page) {
                        if (item.isObject() && "ocr_text".equals(item.path("type").asText())
                                && item.path("bbox").isArray() && item.path("bbox").size() == 4) {
                            ObjectNode box = objectMapper.createObjectNode();
                            box.set("bbox", item.path("bbox"));
                            boxes.add(box);
                        }
                    }
                }
                pages.add(boxes);
            }
            return pages;
        } catch (IOException exception) {
            return objectMapper.createArrayNode();
        }
    }
}
