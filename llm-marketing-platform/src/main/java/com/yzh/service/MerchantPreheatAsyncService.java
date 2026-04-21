package com.yzh.service;

public interface MerchantPreheatAsyncService {

    /**
     * Trigger merchant online activities preheat in background.
     *
     * @param merchantId merchant identity(username)
     */
    void preheatOnlineActivitiesAsync(String merchantId);
}
