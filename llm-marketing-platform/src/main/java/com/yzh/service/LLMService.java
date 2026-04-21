package com.yzh.service;

/**
 * 大模型对话 Service 接口
 */
public interface LLMService {

    /**
     * 向大模型发送消息并获取回复，同时将对话记录存入数据库
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID（同一对话窗口保持一致）
     * @param modelKey  模型标识，如 deepseek
     * @param prompt    用户输入的问题
     * @return AI 回复的文本
     */
    /**
     * 向大模型发送消息并获取回复，同时将对话记录存入数据库
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID（同一对话窗口保持一致）
     * @param modelKey  模型标识，如 deepseek
     * @param prompt    用户输入的问题
     * @return AI 回复的文本
     */
    com.yzh.model.vo.ChatReplyPayload sendMessage(
            String userId,
            String sessionId,
            String modelKey,
            String assistantType,
            String prompt
    );

    /**
     * 获取指定用户的历史会话列表（附带简短标题）
     */
    java.util.List<com.yzh.model.vo.HistorySessionVO> getSessionList(String userId, String assistantType);

    /**
     * 获取指定会话下的所有对话记录
     */
    java.util.List<com.yzh.model.entity.ChatHistory> getSessionMessages(String userId, String sessionId);
}
