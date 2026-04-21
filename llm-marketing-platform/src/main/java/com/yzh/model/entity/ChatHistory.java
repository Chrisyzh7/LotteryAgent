package com.yzh.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 对话记录实体类，对应数据库 chat_history 表
 * 每条消息单独一行，通过 messageRole 区分角色（user / assistant）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_history")
public class ChatHistory {

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private String userId;

    /** 会话 ID，同一对话窗口共享，用于 RAG 检索上下文 */
    private String sessionId;

    /** 模型标识，如 deepseek */
    private String modelKey;

    /** 消息角色：user（用户） / assistant（AI） */
    private String messageRole;

    /** 消息内容 */
    private String content;

    /** 创建时间 */
    private LocalDateTime createTime;
}
