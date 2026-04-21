package com.yzh.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * LLM 工具类
 * 使用 OkHttp 以 OpenAI 兼容格式调用大模型 API（DeepSeek 等）
 */
@Component
public class LLMUtil {

    private static final Logger log = LoggerFactory.getLogger(LLMUtil.class);

    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.get("application/json; charset=utf-8");

    /** OkHttp 客户端（单例复用，连接池）*/
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)   // 大模型响应较慢，设 60s
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * 调用大模型 API，获取 AI 回复
     *
     * @param apiKey     API 鉴权密钥
     * @param baseUrl    请求端点，如 https://api.deepseek.com/v1/chat/completions
     * @param model      模型名称，如 deepseek-chat
     * @param userPrompt 用户输入的问题
     * @return AI 回复的文本内容
     * @throws IOException 网络请求失败时抛出
     */
    public String chat(String apiKey, String baseUrl, String model, com.alibaba.fastjson2.JSONArray messages) throws IOException {
        // 1. 构造请求体（OpenAI 兼容 JSON 格式）
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", model);
        requestBody.put("stream", false);   // 非流式，等待完整回复

        requestBody.put("messages", messages);

        log.info("[LLMUtil] 请求模型={}，携带对话追踪上下文厚度={}", model, messages.size());

        // 2. 构造 OkHttp 请求
        RequestBody body = RequestBody.create(requestBody.toJSONString(), JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(baseUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        // 3. 发起同步请求
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "无响应体";
                log.error("[LLMUtil] API 请求失败，HTTP状态码={}，响应={}", response.code(), errorBody);
                throw new IOException("大模型 API 请求失败，状态码: " + response.code() + "，详情: " + errorBody);
            }

            String responseStr = response.body().string();
            log.info("[LLMUtil] 收到大模型响应，响应长度={}", responseStr.length());

            // 4. 解析响应，提取 AI 回复文本
            // 标准 OpenAI 格式: {"choices":[{"message":{"role":"assistant","content":"..."}}]}
            JSONObject responseJson = JSON.parseObject(responseStr);
            JSONArray choices = responseJson.getJSONArray("choices");

            if (choices == null || choices.isEmpty()) {
                throw new IOException("大模型响应格式异常，choices 为空，原始响应: " + responseStr);
            }

            String aiContent = choices.getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");

            log.info("[LLMUtil] AI 回复内容长度={}", aiContent != null ? aiContent.length() : 0);
            return aiContent;
        }
    }
}
