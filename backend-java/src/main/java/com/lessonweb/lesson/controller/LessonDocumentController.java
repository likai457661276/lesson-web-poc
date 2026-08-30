package com.lessonweb.lesson.controller;

import com.lessonweb.lesson.model.lesson.LessonDocument;
import com.lessonweb.lesson.model.lesson.LessonDocumentSummary;
import com.lessonweb.lesson.service.LessonDocumentLibraryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/lesson-documents")
public class LessonDocumentController {
    private final LessonDocumentLibraryService documents;

    public LessonDocumentController(LessonDocumentLibraryService documents) {
        this.documents = documents;
    }

    @GetMapping
    public List<LessonDocumentSummary> list() { return documents.list(); }

    @GetMapping("/{documentId}")
    public ResponseEntity<LessonDocument> get(@PathVariable String documentId) {
        return response(documents.snapshot(documentId));
    }

    @PutMapping("/{documentId}")
    public ResponseEntity<LessonDocument> update(@PathVariable String documentId, @Valid @RequestBody LessonDocument document,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return response(documents.update(documentId, document, ifMatch));
    }

    private ResponseEntity<LessonDocument> response(LessonDocumentLibraryService.Snapshot snapshot) {
        return ResponseEntity.ok().eTag(snapshot.etag()).header(HttpHeaders.CACHE_CONTROL, "no-store").body(snapshot.document());
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(@PathVariable String documentId) {
        documents.delete(documentId);
        return ResponseEntity.noContent().build();
    }
}
