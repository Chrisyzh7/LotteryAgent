package com.yzh.service;

/**
 * 抽奖智能体 Tool Calls 编排器
 */
public interface LotteryToolOrchestratorService {

    /**
     * 执行工具调用
     *
     * @param merchantId 商户ID
     * @param toolName   工具名
     * @param arguments  JSON 参数对象
     * @return 工具返回对象（可直接给前端渲染）
     */
    Object execute(String merchantId, String toolName, com.alibaba.fastjson2.JSONObject arguments);
}

