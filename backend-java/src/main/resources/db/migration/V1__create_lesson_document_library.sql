CREATE TABLE `lesson_document_conversion` (
  `id` VARCHAR(64) NOT NULL COMMENT '解析任务ID',
  `source_file_name` VARCHAR(500) NOT NULL COMMENT '源文件名',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1处理中 2完成 3失败',
  `error_code` VARCHAR(64) DEFAULT NULL COMMENT '失败错误码',
  `msg` VARCHAR(1000) DEFAULT NULL COMMENT '状态或失败信息',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `completed_at` DATETIME(6) DEFAULT NULL,
  `del_flag` CHAR(1) NOT NULL DEFAULT '0' COMMENT '0存在 2删除',
  PRIMARY KEY (`id`),
  KEY `idx_lesson_document_conversion_status` (`status`, `del_flag`),
  KEY `idx_lesson_document_conversion_updated` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教案文档转换任务';

CREATE TABLE `lesson_document_content` (
  `id` VARCHAR(64) NOT NULL COMMENT '文档ID',
  `conversion_id` VARCHAR(64) NOT NULL COMMENT '转换任务ID',
  `version` VARCHAR(16) NOT NULL,
  `title` VARCHAR(500) NOT NULL,
  `source_type` VARCHAR(64) NOT NULL,
  `source_file_name` VARCHAR(500) NOT NULL,
  `block_count` INT NOT NULL DEFAULT 0,
  `content_json` LONGTEXT NOT NULL COMMENT 'LessonDocument v1 JSON',
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lesson_document_content_conversion` (`conversion_id`),
  KEY `idx_lesson_document_content_updated` (`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教案标准化文档';

CREATE TABLE `lesson_document_asset` (
  `id` VARCHAR(64) NOT NULL,
  `conversion_id` VARCHAR(64) NOT NULL,
  `src` VARCHAR(1000) NOT NULL,
  `relative_path` VARCHAR(1000) NOT NULL,
  `content_type` VARCHAR(128) DEFAULT NULL,
  `size_bytes` BIGINT NOT NULL DEFAULT 0,
  `sha256` CHAR(64) NOT NULL,
  `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_lesson_document_asset_conversion` (`conversion_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教案资源元数据';
