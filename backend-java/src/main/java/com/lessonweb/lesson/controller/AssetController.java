package com.lessonweb.lesson.controller;

import com.lessonweb.lesson.storage.AssetStorageService;
import com.lessonweb.lesson.service.LessonDocumentLibraryService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetStorageService assets;
    private final LessonDocumentLibraryService documents;

    public AssetController(AssetStorageService assets, LessonDocumentLibraryService documents) {
        this.assets = assets;
        this.documents = documents;
    }

    @GetMapping("/{jobId}/{filename:.+}")
    public ResponseEntity<Resource> getAsset(@PathVariable String jobId, @PathVariable String filename) {
        documents.requireActive(jobId);
        FileSystemResource resource = new FileSystemResource(assets.getAsset(jobId, filename));
        MediaType contentType = MediaTypeFactory.getMediaType(filename)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(contentType).body(resource);
    }
}
