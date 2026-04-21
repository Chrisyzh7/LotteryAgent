-- 清理旧 lottery_* 表（若不存在会忽略）
DROP TABLE IF EXISTS `lottery_record`;
DROP TABLE IF EXISTS `lottery_prize`;
DROP TABLE IF EXISTS `lottery_activity`;

-- 保留 prize_type 字段，但改为可空默认 NULL
ALTER TABLE `marketing_prizes`
    MODIFY COLUMN `prize_type` tinyint(2) DEFAULT NULL COMMENT '奖品类型（保留字段，当前不参与业务计算）';

-- 活动页面风格字段（若已存在可忽略该报错）
ALTER TABLE `marketing_activity`
    ADD COLUMN `page_style` varchar(32) DEFAULT 'dark_neon' COMMENT '页面风格：dark_neon/ins_minimal/fresh_light' AFTER `deduct_points`;

-- 活动初始积分字段（若已存在可忽略该报错）
ALTER TABLE `marketing_activity`
    ADD COLUMN `initial_user_points` decimal(10,2) NOT NULL DEFAULT '100.00' COMMENT '用户进入活动的初始积分' AFTER `deduct_points`;

-- C 端用户主表（若不存在则创建）
CREATE TABLE IF NOT EXISTS `c_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `c_user_id` varchar(64) NOT NULL COMMENT 'C端用户唯一标识',
  `username` varchar(64) DEFAULT NULL COMMENT '登录用户名',
  `password_hash` varchar(255) DEFAULT NULL COMMENT '密码哈希',
  `nickname` varchar(128) DEFAULT NULL COMMENT '昵称',
  `mobile` varchar(32) DEFAULT NULL COMMENT '手机号',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_c_user_id` (`c_user_id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='C端用户表';

-- C 端用户活动积分账户（若不存在则创建）
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
