package com.lessonweb.lesson.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("lesson_document_conversion")
public class ConversionEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String sourceFileName;
    private Integer status;
    private String errorCode;
    private String msg;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    private String delFlag;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSourceFileName() { return sourceFileName; }
    public void setSourceFileName(String sourceFileName) { this.sourceFileName = sourceFileName; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
}
