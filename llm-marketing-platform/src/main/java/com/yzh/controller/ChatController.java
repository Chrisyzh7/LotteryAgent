package com.yzh.controller;

import com.yzh.model.vo.ChatRequest;
import com.yzh.model.vo.ChatResponse;
import com.yzh.service.LLMService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * 大模型对话 Controller
 * POST /api/chat/send — 接收用户消息并返回 AI 回复
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final LLMService llmService;

    /**
     * 发送聊天消息
     *
     * @param request 请求体：userId / sessionId / modelKey / prompt
     * @return 包含 AI 回复的统一响应体
     */
    @PostMapping("/send")
    public ChatResponse<com.yzh.model.vo.ChatReplyPayload> sendMessage(@RequestBody ChatRequest request) {
        // 从拦截器上下文中获取真实的登录 userId
        String userId = com.yzh.util.UserContext.getUserId();
        
        log.info("[ChatController] 收到聊天请求 userId={}, sessionId={}, modelKey={}, assistantType={}",
                userId, request.getSessionId(), request.getModelKey(), request.getAssistantType());

        try {
            // 参数校验
            if (request.getPrompt() == null || request.getPrompt().isBlank()) {
                return ChatResponse.error("prompt 不能为空");
            }

            // sessionId 若前端未传，自动生成一个（兜底逻辑）
            String sessionId = (request.getSessionId() != null && !request.getSessionId().isBlank())
                    ? request.getSessionId()
                    : java.util.UUID.randomUUID().toString().replace("-", "");

            String modelKey = (request.getModelKey() != null) ? request.getModelKey() : "deepseek";
            String assistantType = (request.getAssistantType() != null) ? request.getAssistantType() : "general";

            // 调用 Service
            com.yzh.model.vo.ChatReplyPayload reply = llmService.sendMessage(
                    userId,
                    sessionId,
                    modelKey,
                    assistantType,
                    request.getPrompt()
            );

            log.info("[ChatController] 对话成功，sessionId={}, AI 回复长度={}",
                    sessionId, reply.getReplyText() != null ? reply.getReplyText().length() : 0);
            return ChatResponse.success(reply);

        } catch (Exception e) {
            log.error("[ChatController] 处理对话请求失败: {}", e.getMessage(), e);
            return ChatResponse.error("服务暂时不可用：" + e.getMessage());
        }
    }
    /**
     * 获取当前用户的历史会话列表
     */
    @GetMapping("/sessions")
    public ChatResponse<java.util.List<com.yzh.model.vo.HistorySessionVO>> getSessions(
            @RequestParam(value = "assistantType", required = false) String assistantType
    ) {
        String userId = com.yzh.util.UserContext.getUserId();
        try {
            return ChatResponse.success(llmService.getSessionList(userId, assistantType));
        } catch (Exception e) {
            log.error("[ChatController] 获取会话列表失败: {}", e.getMessage());
            return ChatResponse.error("获取会话列表失败");
        }
    }

    /**
     * 获取某个会话的详细聊天记录
     */
    @GetMapping("/messages")
    public ChatResponse<java.util.List<com.yzh.model.entity.ChatHistory>> getMessages(@RequestParam("sessionId") String sessionId) {
        String userId = com.yzh.util.UserContext.getUserId();
        if (sessionId == null || sessionId.isBlank()) {
            return ChatResponse.error("sessionId 不能为空");
        }
        try {
            return ChatResponse.success(llmService.getSessionMessages(userId, sessionId));
        } catch (Exception e) {
            log.error("[ChatController] 获取会话消息失败: {}", e.getMessage());
            return ChatResponse.error("获取会话消息失败");
        }
    }
}
