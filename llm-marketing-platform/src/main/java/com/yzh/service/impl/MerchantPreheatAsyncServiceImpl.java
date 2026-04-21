package com.yzh.service.impl;

import com.yzh.service.LotteryPreheatService;
import com.yzh.service.MerchantPreheatAsyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantPreheatAsyncServiceImpl implements MerchantPreheatAsyncService {

    private final LotteryPreheatService lotteryPreheatService;

    @Override
    @Async("merchantPreheatExecutor")
    public void preheatOnlineActivitiesAsync(String merchantId) {
        if (merchantId == null || merchantId.isBlank()) {
            return;
        }
        long begin = System.currentTimeMillis();
        try {
            log.info("[MerchantPreheatAsync] start preheat after login, merchantId={}", merchantId);
            lotteryPreheatService.preheatOnlineByMerchant(merchantId);
            log.info("[MerchantPreheatAsync] finish preheat after login, merchantId={}, costMs={}",
                    merchantId, System.currentTimeMillis() - begin);
        } catch (Exception e) {
            log.warn("[MerchantPreheatAsync] preheat failed, merchantId={}, err={}",
                    merchantId, e.getMessage(), e);
        }
    }
}
