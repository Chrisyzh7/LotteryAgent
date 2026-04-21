package com.yzh.service;

/**
 * 抽奖活动预热服务
 * 负责将活动的概率映射与库存预加载到 Redis。
 */
public interface LotteryPreheatService {

    /**
     * 预热指定活动
     *
     * @param activityId 活动ID
     */
    void preheat(Long activityId);

    /**
     * Clear Redis preheated lottery cache for one activity.
     *
     * @param activityId activity id
     */
    void clearPreheatCache(Long activityId);

    /**
     * Preheat online activities only for one merchant.
     *
     * @param merchantId merchant identity(username)
     */
    void preheatOnlineByMerchant(String merchantId);
}
