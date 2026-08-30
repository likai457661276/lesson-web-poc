package com.lessonweb.lesson.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lessonweb.lesson.exception.AppException;
import com.lessonweb.lesson.model.job.ErrorDetail;
import com.lessonweb.lesson.model.job.JobStatus;
import com.lessonweb.lesson.model.job.ParseJob;
import com.lessonweb.lesson.model.lesson.LessonDocument;
import com.lessonweb.lesson.persistence.entity.ConversionEntity;
import com.lessonweb.lesson.persistence.entity.DocumentAssetEntity;
import com.lessonweb.lesson.persistence.entity.DocumentContentEntity;
import com.lessonweb.lesson.persistence.mapper.ConversionMapper;
import com.lessonweb.lesson.persistence.mapper.DocumentAssetMapper;
import com.lessonweb.lesson.persistence.mapper.DocumentContentMapper;
import com.lessonweb.lesson.storage.AssetStorageService.StoredAsset;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class ParseJobService {
    private final ConversionMapper conversions;
    private final DocumentContentMapper contents;
    private final DocumentAssetMapper assets;
    private final ObjectMapper objectMapper;

    public ParseJobService(ConversionMapper conversions, DocumentContentMapper contents,
                           DocumentAssetMapper assets, ObjectMapper objectMapper) {
        this.conversions = conversions;
        this.contents = contents;
        this.assets = assets;
        this.objectMapper = objectMapper;
    }

    public ParseJob create(String jobId, String sourceFilename) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        ConversionEntity entity = new ConversionEntity();
        entity.setId(jobId);
        entity.setSourceFileName(sourceFilename);
        entity.setStatus(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setDelFlag("0");
        conversions.insert(entity);
        return toJob(entity, null);
    }

    public ParseJob get(String jobId) {
        ConversionEntity entity = require(jobId);
        DocumentContentEntity content = contents.selectActiveById(jobId);
        return toJob(entity, content == null ? null : deserialize(content.getContentJson()));
    }

    public ParseJob processing(String jobId) {
        update(jobId, 1, null, null, null);
        return get(jobId);
    }

    @Transactional
    public ParseJob success(String jobId, LessonDocument document, List<StoredAsset> storedAssets) {
        require(jobId);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        DocumentContentEntity content = contentEntity(jobId, document, now);
        contents.insert(content);
        for (StoredAsset stored : storedAssets) {
            DocumentAssetEntity asset = new DocumentAssetEntity();
            asset.setId(UUID.randomUUID().toString());
            asset.setConversionId(jobId);
            asset.setSrc(stored.src());
            asset.setRelativePath(stored.relativePath());
            asset.setContentType(stored.contentType());
            asset.setSizeBytes(stored.sizeBytes());
            asset.setSha256(stored.sha256());
            asset.setCreatedAt(now);
            assets.insert(asset);
        }
        update(jobId, 2, null, null, now);
        return get(jobId);
    }

    public ParseJob fail(String jobId, String code, String message) {
        update(jobId, 3, code, message, LocalDateTime.now(ZoneOffset.UTC));
        return get(jobId);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedJobs() {
        conversions.markInterrupted(LocalDateTime.now(ZoneOffset.UTC));
    }

    private DocumentContentEntity contentEntity(String jobId, LessonDocument document, LocalDateTime now) {
        DocumentContentEntity content = new DocumentContentEntity();
        content.setId(document.documentId());
        content.setConversionId(jobId);
        content.setVersion(document.version());
        content.setTitle(document.title());
        content.setSourceType(document.metadata().sourceType());
        content.setSourceFileName(document.metadata().sourceFileName());
        content.setBlockCount(document.blocks().size());
        content.setContentJson(serialize(document));
        content.setCreatedAt(now);
        content.setUpdatedAt(now);
        return content;
    }

    private ConversionEntity require(String id) {
        ConversionEntity entity = conversions.selectActiveById(id);
        if (entity == null) throw new AppException("JOB_NOT_FOUND", "解析任务不存在", HttpStatus.NOT_FOUND);
        return entity;
    }

    private void update(String id, int status, String code, String message, LocalDateTime completedAt) {
        if (conversions.updateState(id, status, code, message, completedAt, LocalDateTime.now(ZoneOffset.UTC)) == 0) {
            throw new AppException("JOB_NOT_FOUND", "解析任务不存在", HttpStatus.NOT_FOUND);
        }
    }

    private ParseJob toJob(ConversionEntity entity, LessonDocument document) {
        JobStatus status = switch (entity.getStatus()) {
            case 0 -> JobStatus.PENDING;
            case 1 -> JobStatus.PROCESSING;
            case 2 -> JobStatus.COMPLETED;
            default -> JobStatus.FAILED;
        };
        ErrorDetail error = entity.getErrorCode() == null ? null : new ErrorDetail(entity.getErrorCode(), entity.getMsg());
        Instant createdAt = entity.getCreatedAt().toInstant(ZoneOffset.UTC);
        return new ParseJob(entity.getId(), status, entity.getSourceFileName(), createdAt, document, error);
    }

    private String serialize(LessonDocument document) {
        try {
            return objectMapper.writeValueAsString(document);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("LessonDocument 序列化失败", exception);
        }
    }

    private LessonDocument deserialize(String json) {
        try {
            return objectMapper.readValue(json, LessonDocument.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("LessonDocument 读取失败", exception);
        }
    }
}
