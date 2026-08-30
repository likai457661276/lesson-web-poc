package com.lessonweb.lesson.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lessonweb.lesson.exception.AppException;
import com.lessonweb.lesson.model.lesson.LessonDocument;
import com.lessonweb.lesson.model.lesson.LessonDocumentSummary;
import com.lessonweb.lesson.persistence.entity.DocumentContentEntity;
import com.lessonweb.lesson.persistence.mapper.ConversionMapper;
import com.lessonweb.lesson.persistence.mapper.DocumentContentMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class LessonDocumentLibraryService {
    private final DocumentContentMapper contents;
    private final ConversionMapper conversions;
    private final ObjectMapper objectMapper;

    public LessonDocumentLibraryService(DocumentContentMapper contents, ConversionMapper conversions, ObjectMapper objectMapper) {
        this.contents = contents;
        this.conversions = conversions;
        this.objectMapper = objectMapper;
    }

    public List<LessonDocumentSummary> list() {
        return contents.selectActiveSummaries().stream().map(row -> new LessonDocumentSummary(
                row.getId(), row.getTitle(), row.getSourceFileName(), row.getSourceType(), row.getBlockCount(),
                row.getCreatedAt().toInstant(ZoneOffset.UTC), row.getUpdatedAt().toInstant(ZoneOffset.UTC))).toList();
    }

    public LessonDocument get(String id) {
        DocumentContentEntity entity = contents.selectActiveById(id);
        if (entity == null) throw notFound();
        return deserialize(entity.getContentJson());
    }

    @Transactional
    public LessonDocument update(String id, LessonDocument document) {
        if (!id.equals(document.documentId())) {
            throw new AppException("DOCUMENT_ID_MISMATCH", "路径中的文档 ID 与请求内容不一致", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        DocumentContentEntity entity = new DocumentContentEntity();
        entity.setId(id); entity.setVersion(document.version()); entity.setTitle(document.title());
        entity.setSourceType(document.metadata().sourceType()); entity.setSourceFileName(document.metadata().sourceFileName());
        entity.setBlockCount(document.blocks().size()); entity.setContentJson(serialize(document));
        entity.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        if (contents.updateActive(entity) == 0) throw notFound();
        return get(id);
    }

    @Transactional
    public void delete(String id) {
        if (conversions.softDelete(id, LocalDateTime.now(ZoneOffset.UTC)) == 0) throw notFound();
    }

    public void requireActive(String id) {
        if (conversions.selectActiveById(id) == null) throw notFound();
    }

    private String serialize(LessonDocument document) {
        try { return objectMapper.writeValueAsString(document); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("LessonDocument 序列化失败", exception); }
    }

    private LessonDocument deserialize(String json) {
        try { return objectMapper.readValue(json, LessonDocument.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("LessonDocument 读取失败", exception); }
    }

    private AppException notFound() {
        return new AppException("DOCUMENT_NOT_FOUND", "文档不存在", HttpStatus.NOT_FOUND);
    }
}
