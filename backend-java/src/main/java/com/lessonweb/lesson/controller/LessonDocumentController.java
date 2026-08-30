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
    public LessonDocument get(@PathVariable String documentId) { return documents.get(documentId); }

    @PutMapping("/{documentId}")
    public LessonDocument update(@PathVariable String documentId, @Valid @RequestBody LessonDocument document) {
        return documents.update(documentId, document);
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(@PathVariable String documentId) {
        documents.delete(documentId);
        return ResponseEntity.noContent().build();
    }
}
