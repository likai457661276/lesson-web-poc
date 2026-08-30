package com.lessonweb.lesson.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("lesson_document_asset")
public class DocumentAssetEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String conversionId;
    private String src;
    private String relativePath;
    private String contentType;
    private Long sizeBytes;
    private String sha256;
    private LocalDateTime createdAt;
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getConversionId() { return conversionId; }
    public void setConversionId(String conversionId) { this.conversionId = conversionId; }
    public String getSrc() { return src; }
    public void setSrc(String src) { this.src = src; }
    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
