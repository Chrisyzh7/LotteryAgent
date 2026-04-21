package com.yzh.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzh.config.LlmProperties;
import com.yzh.mapper.ChatHistoryMapper;
import com.yzh.mapper.MarketingActivityMapper;
import com.yzh.model.entity.ChatHistory;
import com.yzh.model.entity.MarketingActivity;
import com.yzh.model.vo.ChatReplyPayload;
import com.yzh.model.vo.HistorySessionVO;
import com.yzh.service.LLMService;
import com.yzh.service.LotteryToolOrchestratorService;
import com.yzh.util.LLMUtil;
import com.yzh.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class LLMServiceImpl implements LLMService {

    private static final Logger log = LoggerFactory.getLogger(LLMServiceImpl.class);

    private final LLMUtil llmUtil;
    private final LlmProperties llmProperties;
    private final ChatHistoryMapper chatHistoryMapper;
    private final MarketingActivityMapper marketingActivityMapper;
    private final LotteryToolOrchestratorService lotteryToolOrchestratorService;

    @Override
    public ChatReplyPayload sendMessage(String userId, String sessionId, String modelKey, String assistantType, String prompt) {
        String normalizedAssistantType = normalizeAssistantType(assistantType);
        String merchantIdentity = resolveMerchantIdentity(userId);
        log.info("[LLMService] start chat userId={}, sessionId={}, modelKey={}, assistantType={}",
                userId, sessionId, modelKey, normalizedAssistantType);

        ChatHistory userRecord = ChatHistory.builder()
                .userId(userId)
                .sessionId(sessionId)
                .modelKey(modelKey)
                .messageRole("user")
                .content(prompt)
                .createTime(LocalDateTime.now())
                .build();
        chatHistoryMapper.insert(userRecord);

        if ("lottery_agent".equals(normalizedAssistantType) && isManagementLinkRequest(prompt)) {
            ChatReplyPayload quickPayload = buildMerchantLinkPayload(merchantIdentity);
            persistAssistantReply(userId, sessionId, modelKey, quickPayload);
            return quickPayload;
        }

        List<ChatHistory> historyList = chatHistoryMapper.selectList(
                new LambdaQueryWrapper<ChatHistory>()
                        .eq(ChatHistory::getSessionId, sessionId)
                        .orderByDesc(ChatHistory::getCreateTime)
                        .last("LIMIT 20")
        );
        Collections.reverse(historyList);

        JSONArray messagesArr = new JSONArray();
        for (ChatHistory h : historyList) {
            JSONObject msgObj = new JSONObject();
            msgObj.put("role", h.getMessageRole());
            msgObj.put("content", h.getContent());
            messagesArr.add(msgObj);
        }

        LlmProperties.ModelConfig config = llmProperties.getProviders().get(modelKey);
        if (config == null) {
            throw new RuntimeException("未配置模型: " + modelKey);
        }

        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", buildSystemPrompt(config.getModel(), normalizedAssistantType));
        messagesArr.add(0, systemMsg);

        String aiResponse;
        try {
            aiResponse = llmUtil.chat(
                    config.getApiKey(),
                    config.getBaseUrl(),
                    config.getModel(),
                    messagesArr
            );
        } catch (Exception e) {
            log.error("[LLMService] call llm failed: {}", e.getMessage(), e);
            throw new RuntimeException("模型调用失败: " + e.getMessage(), e);
        }

        ChatReplyPayload payload = ChatReplyPayload.builder()
                .assistantType(normalizedAssistantType)
                .toolExecuted(false)
                .replyText(aiResponse)
                .build();

        if ("lottery_agent".equals(normalizedAssistantType)) {
            ToolCall call = extractToolCall(aiResponse);
            if (call != null) {
                if (!canExecuteToolCall(prompt, call.name)) {
                    payload.setToolExecuted(false);
                    payload.setToolName(null);
                    payload.setToolData(null);
                    payload.setReplyText(buildBlockedToolMessage(call.name));
                } else {
                    try {
                        if ("create_marketing_activity".equals(call.name) && call.arguments != null) {
                            call.arguments.putIfAbsent("_rawPrompt", prompt);
                        }
                        Object toolData = lotteryToolOrchestratorService.execute(merchantIdentity, call.name, call.arguments);
                        payload.setToolExecuted(true);
                        payload.setToolName(call.name);
                        payload.setToolData(toolData);
                        payload.setReplyText(buildToolSuccessMessage(call.name, toolData));
                    } catch (Exception toolEx) {
                        log.error("[LLMService] tool execute failed toolName={}, err={}", call.name, toolEx.getMessage(), toolEx);
                        payload.setReplyText("我识别到了工具调用意图，但执行失败："
                                + toolEx.getMessage()
                                + "。请补充信息后再试。");
                    }
                }
            }
        }

        persistAssistantReply(userId, sessionId, modelKey, payload);
        return payload;
    }

    @Override
    public List<HistorySessionVO> getSessionList(String userId, String assistantType) {
        String normalizedAssistantType = normalizeAssistantType(assistantType);
        List<HistorySessionVO> all = chatHistoryMapper.selectSessionList(userId);
        List<HistorySessionVO> filtered = new ArrayList<>();

        for (HistorySessionVO vo : all) {
            if (vo.getSessionId() == null) {
                continue;
            }
            boolean isLottery = vo.getSessionId().startsWith("lottery_");
            if ("lottery_agent".equals(normalizedAssistantType) && isLottery) {
                filtered.add(vo);
            }
            if ("general".equals(normalizedAssistantType) && !isLottery) {
                filtered.add(vo);
            }
        }
        return filtered;
    }
    @Override
    public List<ChatHistory> getSessionMessages(String userId, String sessionId) {
        return chatHistoryMapper.selectList(
                new LambdaQueryWrapper<ChatHistory>()
                        .eq(ChatHistory::getUserId, userId)
                        .eq(ChatHistory::getSessionId, sessionId)
                        .orderByAsc(ChatHistory::getCreateTime)
        );
    }

    private String normalizeAssistantType(String assistantType) {
        return "lottery_agent".equalsIgnoreCase(assistantType) ? "lottery_agent" : "general";
    }

    private String buildSystemPrompt(String currentModelName, String assistantType) {
        if ("lottery_agent".equals(assistantType)) {
            return "你是“抽奖活动创建助手”，可以正常聊天，也可以在满足条件时调用后端工具。"
                    + "当用户明确要创建活动或发布活动时，才输出 JSON 工具调用；否则输出普通文本。"
                    + "可用工具："
                    + "1) create_marketing_activity，标准参数结构："
                    + "{\"activityName\":\"\",\"deductPoints\":10,\"pageStyle\":\"dark_neon|ins_minimal|fresh_light\","
                    + "\"prizes\":[{\"prizeName\":\"\",\"totalStock\":100,\"probability\":0.1}]}"
                    + "（prizes 里的键必须优先使用 prizeName、totalStock、probability 或 weight）;"
                    + "2) publish_marketing_activity，参数：{\"activityId\":123}。"
                    + "工具调用格式只允许以下 JSON："
                    + "{\"tool_call\":{\"name\":\"create_marketing_activity\",\"arguments\":{...}}}"
                    + "或"
                    + "{\"tool_call\":{\"name\":\"publish_marketing_activity\",\"arguments\":{...}}}";
        }
        return "你是通用对话助手，当前模型：" + currentModelName + "。请直接回答用户问题。";
    }

    private ToolCall extractToolCall(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        List<String> candidates = new ArrayList<>();
        candidates.add(text.trim());

        Pattern fencedJson = Pattern.compile("```json\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
        Matcher fencedMatcher = fencedJson.matcher(text);
        while (fencedMatcher.find()) {
            if (fencedMatcher.groupCount() >= 1) {
                candidates.add(fencedMatcher.group(1).trim());
            }
        }

        Pattern wrapperPattern = Pattern.compile("\\{\\s*\"tool_call\"\\s*:\\s*\\{[\\s\\S]*}}", Pattern.CASE_INSENSITIVE);
        Matcher wrapperMatcher = wrapperPattern.matcher(text);
        while (wrapperMatcher.find()) {
            candidates.add(wrapperMatcher.group().trim());
        }

        for (String candidate : candidates) {
            try {
                JSONObject obj = JSON.parseObject(candidate);
                if (obj == null) {
                    continue;
                }

                JSONObject wrapper = obj.getJSONObject("tool_call");
                if (wrapper != null) {
                    String name = wrapper.getString("name");
                    JSONObject args = toJSONObject(wrapper.get("arguments"));
                    if (name != null && !name.isBlank()) {
                        return new ToolCall(name, args == null ? new JSONObject() : args);
                    }
                }

                String name = obj.getString("name");
                JSONObject args = toJSONObject(obj.get("arguments"));
                if (name != null && !name.isBlank()) {
                    return new ToolCall(name, args == null ? new JSONObject() : args);
                }
            } catch (Exception ignore) {
                // ignore parse failures
            }
        }
        return null;
    }

    private JSONObject toJSONObject(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JSONObject) {
            return (JSONObject) value;
        }
        if (value instanceof String) {
            try {
                return JSON.parseObject((String) value);
            } catch (Exception ignore) {
                return null;
            }
        }
        return null;
    }

    private String buildToolSuccessMessage(String toolName, Object toolData) {
        if ("create_marketing_activity".equals(toolName)) {
            if (toolData instanceof Map) {
                Object activityId = ((Map<?, ?>) toolData).get("activityId");
                Object playLink = ((Map<?, ?>) toolData).get("playLink");
                return "已为你创建活动草稿，activityId=" + activityId + "。可继续发布并预热，活动链接：" + playLink;
            }
            return "活动草稿创建成功。";
        }
        if ("publish_marketing_activity".equals(toolName)) {
            return "活动已发布并预热完成，C 端用户现在可以参与抽奖。";
        }
        if ("get_merchant_activity_links".equals(toolName)) {
            return "已为你返回最近活动的 C 端页面和商家发奖管理链接。";
        }
        return "工具执行成功。";
    }

    private String buildBlockedToolMessage(String toolName) {
        if ("create_marketing_activity".equals(toolName)) {
            return "检测到创建工具调用，但当前消息更像普通问答或信息不完整。"
                    + "如果你要创建活动，请明确给出活动名称、奖品、库存、概率/权重。";
        }
        if ("publish_marketing_activity".equals(toolName)) {
            return "检测到发布工具调用，但当前消息没有明确发布意图。"
                    + "如需发布，请直接说“发布活动 activityId=xx”。";
        }
        return "当前消息不满足工具调用条件，已转为普通对话。";
    }

    private void persistAssistantReply(String userId, String sessionId, String modelKey, ChatReplyPayload payload) {
        ChatHistory assistantRecord = ChatHistory.builder()
                .userId(userId)
                .sessionId(sessionId)
                .modelKey(modelKey)
                .messageRole("assistant")
                .content(payload.getReplyText())
                .createTime(LocalDateTime.now())
                .build();
        chatHistoryMapper.insert(assistantRecord);
    }

    private String resolveMerchantIdentity(String userId) {
        String merchantIdentity = UserContext.getUsername();
        if (merchantIdentity == null || merchantIdentity.isBlank()) {
            merchantIdentity = userId;
        }
        return merchantIdentity;
    }

    private boolean isManagementLinkRequest(String prompt) {
        String text = prompt == null ? "" : prompt;
        return Pattern.compile("(商家管理链接|发奖管理|管理链接|当前活动列表|活动管理|奖励管理|reward-center)", Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .find();
    }

    private boolean isCreateIntent(String prompt) {
        String text = prompt == null ? "" : prompt;
        if (text.isBlank()) {
            return false;
        }
        if (isManagementLinkRequest(text)) {
            return false;
        }

        boolean hasAction = Pattern.compile("(创建|新建|生成|配置|设计|搭建|做一个|我要办|帮我做|帮我生成)", Pattern.CASE_INSENSITIVE)
                .matcher(text).find();
        boolean hasLotteryDomain = Pattern.compile("(抽奖|转盘|九宫格|活动)", Pattern.CASE_INSENSITIVE)
                .matcher(text).find();
        boolean hasStructuredFields = Pattern.compile("(活动名称\\s*[:：]|每次抽奖消耗积分\\s*[:：]|奖品\\s*[:：]|库存\\s*[:：]?\\s*\\d+)", Pattern.CASE_INSENSITIVE)
                .matcher(text).find();
        boolean hasPrizeField = Pattern.compile("奖品\\s*[:：]", Pattern.CASE_INSENSITIVE)
                .matcher(text).find();

        return (hasAction && hasLotteryDomain) || (hasStructuredFields && hasPrizeField);
    }

    private boolean isPublishIntent(String prompt) {
        String text = prompt == null ? "" : prompt;
        return Pattern.compile("(发布|上线|开始活动|预热|发布并预热)", Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .find();
    }

    private boolean canExecuteToolCall(String prompt, String toolName) {
        if ("create_marketing_activity".equals(toolName)) {
            return isCreateIntent(prompt);
        }
        if ("publish_marketing_activity".equals(toolName)) {
            return isPublishIntent(prompt);
        }
        return true;
    }

    private ChatReplyPayload buildMerchantLinkPayload(String merchantId) {
        MarketingActivity latest = marketingActivityMapper.selectOne(
                new LambdaQueryWrapper<MarketingActivity>()
                        .eq(MarketingActivity::getMerchantId, merchantId)
                        .orderByDesc(MarketingActivity::getCreateTime)
                        .last("LIMIT 1")
        );

        if (latest == null) {
            return ChatReplyPayload.builder()
                    .assistantType("lottery_agent")
                    .toolExecuted(false)
                    .replyText("你目前还没有创建过活动。请先创建一个抽奖活动，再让我给你商家管理链接。")
                    .build();
        }

        String style = latest.getPageStyle() == null || latest.getPageStyle().isBlank()
                ? "dark_neon"
                : latest.getPageStyle();
        String playLink = "/lottery.html?activityId=" + latest.getId() + "&style=" + style;
        String manageLink = "/reward-center.html?activityId=" + latest.getId();

        Map<String, Object> toolData = new HashMap<>();
        toolData.put("activityId", latest.getId());
        toolData.put("activityName", latest.getActivityName());
        toolData.put("pageStyle", style);
        toolData.put("playLink", playLink);
        toolData.put("manageLink", manageLink);

        String reply = "已为你找到最近活动链接：\n"
                + "C 端活动页：" + playLink + "\n"
                + "商家发奖管理：" + manageLink;

        return ChatReplyPayload.builder()
                .assistantType("lottery_agent")
                .toolExecuted(true)
                .toolName("get_merchant_activity_links")
                .toolData(toolData)
                .replyText(reply)
                .build();
    }

    private static class ToolCall {
        private final String name;
        private final JSONObject arguments;

        private ToolCall(String name, JSONObject arguments) {
            this.name = name;
            this.arguments = arguments;
        }
    }
}
