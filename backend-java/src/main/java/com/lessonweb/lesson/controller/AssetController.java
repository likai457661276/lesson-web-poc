package com.lessonweb.lesson.controller;

import com.lessonweb.lesson.storage.AssetStorageService;
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

    public AssetController(AssetStorageService assets) {
        this.assets = assets;
    }

    @GetMapping("/{jobId}/{filename:.+}")
    public ResponseEntity<Resource> getAsset(@PathVariable String jobId, @PathVariable String filename) {
        FileSystemResource resource = new FileSystemResource(assets.getAsset(jobId, filename));
        MediaType contentType = MediaTypeFactory.getMediaType(filename)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(contentType).body(resource);
    }
}
