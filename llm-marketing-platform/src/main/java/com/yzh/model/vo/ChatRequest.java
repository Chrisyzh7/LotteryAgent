package com.yzh.model.vo;

import lombok.Data;

/**
 * 发送聊天消息的请求体 VO
 */
@Data
public class ChatRequest {

    /** 用户 ID */
    private String userId;

    /**
     * 会话 ID，同一对话窗口前端维持同一个值
     * 若前端不传，后端自动生成（UUID）
     */
    private String sessionId;

    /** 模型标识，如 deepseek */
    private String modelKey;

    /** 助手类型：general(默认) / lottery_agent */
    private String assistantType;

    /** 用户输入的问题/提示词 */
    private String prompt;
}
