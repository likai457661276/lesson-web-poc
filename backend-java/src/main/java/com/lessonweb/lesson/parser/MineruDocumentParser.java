package com.lessonweb.lesson.parser;

import com.lessonweb.lesson.client.MineruClient;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class MineruDocumentParser {

    private final MineruClient client;
    private final MineruResultExtractor extractor;

    public MineruDocumentParser(MineruClient client, MineruResultExtractor extractor) {
        this.client = client;
        this.extractor = extractor;
    }

    public MineruParseResult parse(Path file) {
        MineruClient.UploadTarget upload = client.createUpload(file.getFileName().toString());
        client.uploadFile(upload.uploadUrl(), file);
        String zipUrl = client.waitForResult(upload.batchId(), file.getFileName().toString());
        byte[] archive = client.downloadZip(zipUrl);
        Path resultDir = file.getParent().resolve("mineru");
        Path archivePath = file.getParent().resolve("mineru-result.zip");
        return extractor.extract(archive, archivePath, resultDir);
    }
}
