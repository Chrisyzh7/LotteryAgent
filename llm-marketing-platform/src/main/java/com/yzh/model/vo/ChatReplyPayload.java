package com.yzh.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对话回复载体：
 * - replyText: 展示给用户的自然语言
 * - toolExecuted/toolName/toolData: 后端 Tool Calls 执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatReplyPayload {

    private String replyText;

    private String assistantType;

    private Boolean toolExecuted;

    private String toolName;

    private Object toolData;
}

