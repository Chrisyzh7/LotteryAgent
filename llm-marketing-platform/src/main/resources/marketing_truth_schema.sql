-- =========================================================
-- 大营销真理域（Truth Domain）核心表
-- =========================================================

-- 1) B 端商户表（可与现有 user_account 逐步迁移）
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_id` varchar(64) NOT NULL COMMENT '商户唯一标识',
  `username` varchar(64) NOT NULL COMMENT '登录账号',
  `password` varchar(255) NOT NULL COMMENT '加密密码',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_id` (`merchant_id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='B端商户用户表';

-- 2) C 端用户全局唯一表（SSOT）
CREATE TABLE IF NOT EXISTS `c_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `c_user_id` varchar(64) NOT NULL COMMENT 'C端全局唯一用户标识',
  `username` varchar(64) DEFAULT NULL COMMENT '登录用户名',
  `password_hash` varchar(255) DEFAULT NULL COMMENT '密码哈希',
  `nickname` varchar(128) DEFAULT NULL COMMENT '昵称',
  `mobile` varchar(32) DEFAULT NULL COMMENT '手机号',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_c_user_id` (`c_user_id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='C端用户全局表';

-- 3) 活动主表
CREATE TABLE IF NOT EXISTS `marketing_activity` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '活动ID',
  `merchant_id` varchar(64) NOT NULL COMMENT '归属商户ID',
  `activity_name` varchar(128) NOT NULL COMMENT '活动名称',
  `deduct_points` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '单次抽奖消耗积分',
  `initial_user_points` decimal(10,2) NOT NULL DEFAULT '100.00' COMMENT '用户进入活动时的初始积分',
  `page_style` varchar(32) DEFAULT 'dark_neon' COMMENT '页面风格：dark_neon/ins_minimal/fresh_light',
  `status` tinyint(2) NOT NULL DEFAULT '0' COMMENT '状态：0-草稿/下线，1-上线，2-结束',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_merchant_status` (`merchant_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='营销活动主表';

-- 4) 奖品配置表
CREATE TABLE IF NOT EXISTS `marketing_prizes` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '奖品ID',
  `activity_id` bigint(20) NOT NULL COMMENT '归属活动ID',
  `prize_name` varchar(128) NOT NULL COMMENT '奖品名称',
  `prize_type` tinyint(2) DEFAULT NULL COMMENT '奖品类型（保留字段，当前不参与业务计算）',
  `weight` int(11) NOT NULL DEFAULT '0' COMMENT '中奖权重',
  `total_stock` int(11) NOT NULL DEFAULT '0' COMMENT '总库存',
  `surplus_stock` int(11) NOT NULL DEFAULT '0' COMMENT '剩余库存',
  `prize_image` varchar(256) DEFAULT NULL COMMENT '奖品图标',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_activity_id` (`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='营销奖品配置表';

-- 5) C端用户-活动积分账户（后端真值）
CREATE TABLE IF NOT EXISTS `c_user_points` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `activity_id` bigint(20) NOT NULL COMMENT '活动ID',
  `c_user_id` varchar(64) NOT NULL COMMENT 'C端用户ID',
  `total_points` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '累计发放积分',
  `used_points` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '累计使用积分',
  `remain_points` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '剩余积分',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_user` (`activity_id`,`c_user_id`),
  KEY `idx_c_user_id` (`c_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='C端用户活动积分账户';

-- 5) 发奖流水确权表（单活动-奖品映射）
CREATE TABLE IF NOT EXISTS `user_rewards` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `activity_id` bigint(20) NOT NULL COMMENT '活动ID',
  `c_user_id` varchar(64) NOT NULL COMMENT 'C端用户ID',
  `prize_id` bigint(20) DEFAULT NULL COMMENT '奖品ID',
  `request_id` varchar(128) NOT NULL COMMENT '幂等请求ID',
  `award_state` tinyint(2) NOT NULL DEFAULT '0' COMMENT '0-待发奖，1-成功，2-失败',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_id` (`request_id`),
  KEY `idx_user_activity` (`c_user_id`,`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发奖流水确权表';
