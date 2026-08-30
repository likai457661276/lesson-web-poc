package com.lessonweb.lesson.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lessonweb.lesson.persistence.entity.DocumentContentEntity;
import com.lessonweb.lesson.persistence.entity.DocumentSummaryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocumentContentMapper extends BaseMapper<DocumentContentEntity> {
    DocumentContentEntity selectActiveById(@Param("id") String id);
    List<DocumentSummaryRow> selectActiveSummaries();
    int updateActive(@Param("entity") DocumentContentEntity entity, @Param("expectedJson") String expectedJson);
}
