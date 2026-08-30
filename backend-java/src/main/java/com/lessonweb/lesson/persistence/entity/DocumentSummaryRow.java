package com.lessonweb.lesson.persistence.entity;

import java.time.LocalDateTime;

public class DocumentSummaryRow {
    private String id;
    private String title;
    private String sourceFileName;
    private String sourceType;
    private Integer blockCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSourceFileName() { return sourceFileName; }
    public void setSourceFileName(String sourceFileName) { this.sourceFileName = sourceFileName; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Integer getBlockCount() { return blockCount; }
    public void setBlockCount(Integer blockCount) { this.blockCount = blockCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
