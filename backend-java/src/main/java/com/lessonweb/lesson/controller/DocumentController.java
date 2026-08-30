package com.lessonweb.lesson.controller;

import com.lessonweb.lesson.model.docx.DocxExportRequest;
import com.lessonweb.lesson.model.docx.BatchDocxExportRequest;
import com.lessonweb.lesson.docx.BatchDocxExportService;
import com.lessonweb.lesson.docx.HtmlToDocxService;
import com.lessonweb.lesson.model.job.ParseJob;
import com.lessonweb.lesson.service.DocumentParseService;
import javax.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final MediaType DOCX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private final DocumentParseService parseService;
    private final HtmlToDocxService docxService;
    private final BatchDocxExportService batchDocxService;

    public DocumentController(
            DocumentParseService parseService,
            HtmlToDocxService docxService,
            BatchDocxExportService batchDocxService
    ) {
        this.parseService = parseService;
        this.docxService = docxService;
        this.batchDocxService = batchDocxService;
    }

    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ParseJob> parse(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(parseService.submit(file));
    }

    @GetMapping("/{jobId}")
    public ParseJob get(@PathVariable String jobId) {
        return parseService.getJob(jobId);
    }

    @PostMapping(value = "/export-docx", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> exportDocx(@Valid @RequestBody DocxExportRequest request) {
        HtmlToDocxService.ExportResult result = docxService.export(request.getHtml(), request.getFilename());
        String encodedFilename = UriUtils.encode(result.filename(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(DOCX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"lesson.docx\"; filename*=UTF-8''" + encodedFilename)
                .body(result.content());
    }

    @PostMapping(value = "/export-docx-batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> exportDocxBatch(@Valid @RequestBody BatchDocxExportRequest request) {
        byte[] archive = batchDocxService.export(request.documents());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"lesson-documents.zip\"")
                .body(archive);
    }
}
