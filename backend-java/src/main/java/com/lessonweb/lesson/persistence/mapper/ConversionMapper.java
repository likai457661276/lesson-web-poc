package com.lessonweb.lesson.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lessonweb.lesson.persistence.entity.ConversionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface ConversionMapper extends BaseMapper<ConversionEntity> {
    ConversionEntity selectActiveById(@Param("id") String id);
    int updateState(@Param("id") String id, @Param("status") int status,
                    @Param("errorCode") String errorCode, @Param("msg") String msg,
                    @Param("completedAt") LocalDateTime completedAt, @Param("updatedAt") LocalDateTime updatedAt);
    int markInterrupted(@Param("updatedAt") LocalDateTime updatedAt);
    int softDelete(@Param("id") String id, @Param("updatedAt") LocalDateTime updatedAt);
}
