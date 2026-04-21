-- 大模型对话历史记录表
CREATE TABLE IF NOT EXISTS `chat_history` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`    VARCHAR(64)  NOT NULL                COMMENT '用户ID',
    `model_key`  VARCHAR(64)  NOT NULL                COMMENT '模型标识，如 deepseek',
    `prompt`     TEXT         NOT NULL                COMMENT '用户输入的问题',
    `response`   TEXT         NOT NULL                COMMENT 'AI 的回复内容',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='大模型对话历史记录';
