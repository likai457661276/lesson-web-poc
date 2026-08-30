package com.lessonweb.lesson.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("lesson_document_content")
public class DocumentContentEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String conversionId;
    private String version;
    private String title;
    private String sourceType;
    private String sourceFileName;
    private Integer blockCount;
    private String contentJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getConversionId() { return conversionId; }
    public void setConversionId(String conversionId) { this.conversionId = conversionId; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceFileName() { return sourceFileName; }
    public void setSourceFileName(String sourceFileName) { this.sourceFileName = sourceFileName; }
    public Integer getBlockCount() { return blockCount; }
    public void setBlockCount(Integer blockCount) { this.blockCount = blockCount; }
    public String getContentJson() { return contentJson; }
    public void setContentJson(String contentJson) { this.contentJson = contentJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
