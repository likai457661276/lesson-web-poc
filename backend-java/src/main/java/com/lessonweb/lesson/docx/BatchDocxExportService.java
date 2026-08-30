package com.lessonweb.lesson.docx;

import com.lessonweb.lesson.exception.AppException;
import com.lessonweb.lesson.model.docx.DocxExportRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class BatchDocxExportService {
    private final HtmlToDocxService docxService;

    public BatchDocxExportService(HtmlToDocxService docxService) {
        this.docxService = docxService;
    }

    public byte[] export(List<DocxExportRequest> documents) {
        long totalHtmlLength = documents.stream().mapToLong(document -> document.getHtml().length()).sum();
        if (totalHtmlLength > 25_000_000) {
            throw new AppException("BATCH_EXPORT_TOO_LARGE", "所选文档内容过大，请分批下载", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        // Build the complete archive before responding so a failed document never yields a partial ZIP.
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Set<String> filenames = new HashSet<>();
        long totalBytes = 0;
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (DocxExportRequest document : documents) {
                HtmlToDocxService.ExportResult result = docxService.export(document.getHtml(), document.getFilename());
                totalBytes += result.content().length;
                if (totalBytes > 100_000_000) {
                    throw new AppException("BATCH_EXPORT_TOO_LARGE", "生成的文档过大，请分批下载", HttpStatus.PAYLOAD_TOO_LARGE);
                }
                zip.putNextEntry(new ZipEntry(uniqueFilename(result.filename(), filenames)));
                zip.write(result.content());
                zip.closeEntry();
            }
        } catch (IOException exception) {
            throw new AppException("BATCH_EXPORT_FAILED", "文档打包失败，请重试", HttpStatus.INTERNAL_SERVER_ERROR, exception);
        }
        return output.toByteArray();
    }

    private String uniqueFilename(String filename, Set<String> filenames) {
        String candidate = filename;
        String stem = filename.substring(0, filename.length() - ".docx".length());
        int suffix = 2;
        while (!filenames.add(candidate.toLowerCase(Locale.ROOT))) {
            candidate = stem + " (" + suffix++ + ").docx";
        }
        return candidate;
    }
}
