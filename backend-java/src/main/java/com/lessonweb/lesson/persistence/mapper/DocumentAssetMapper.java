package com.lessonweb.lesson.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lessonweb.lesson.persistence.entity.DocumentAssetEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocumentAssetMapper extends BaseMapper<DocumentAssetEntity> {
}
