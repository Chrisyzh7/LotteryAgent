-- 给 chat_history 表增加 session_id 字段（用于区分不同会话，RAG检索时按session分组）
ALTER TABLE `chat_history`
    ADD COLUMN `session_id` varchar(64) NOT NULL DEFAULT '' COMMENT '会话ID，同一对话窗口共享同一个sessionId' AFTER `user_id`,
    ADD INDEX `idx_session_id` (`session_id`);
