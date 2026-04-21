package com.yzh.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 大模型 API 动态配置属性
 * 对应 application.yml 中 llm.providers 前缀的配置项
 */
@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /** 多模型服务商字典映射 */
    private java.util.Map<String, ModelConfig> providers = new java.util.HashMap<>();

    @Data
    public static class ModelConfig {
        /** API Key */
        private String apiKey;
        /** 请求端点，例如 https://api.deepseek.com/v1/chat/completions */
        private String baseUrl;
        /** 使用的模型名称，例如 deepseek-chat */
        private String model;
    }
}
