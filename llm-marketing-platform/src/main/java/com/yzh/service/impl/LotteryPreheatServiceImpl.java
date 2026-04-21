package com.yzh.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzh.draw.model.PrizeRateInfo;
import com.yzh.draw.strategy.IDrawAlgorithm;
import com.yzh.mapper.MarketingActivityMapper;
import com.yzh.mapper.MarketingPrizeMapper;
import com.yzh.model.entity.MarketingActivity;
import com.yzh.model.entity.MarketingPrize;
import com.yzh.service.LotteryPreheatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class LotteryPreheatServiceImpl implements LotteryPreheatService {

    private static final String RATE_MAP_KEY_PREFIX = "lottery_map:";
    private static final String STOCK_KEY_PREFIX = "lottery_stock:";
    private static final String REQUEST_LOCK_KEY_PREFIX = "lottery_req_lock:";
    private static final int RATE_BASE = 10000;

    private final MarketingActivityMapper activityMapper;
    private final MarketingPrizeMapper prizeMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final java.util.Map<String, IDrawAlgorithm> algorithmMap;

    @Override
    public void preheat(Long activityId) {
        if (activityId == null || activityId <= 0) {
            throw new IllegalArgumentException("activityId 非法");
        }

        MarketingActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new IllegalArgumentException("活动不存在，activityId=" + activityId);
        }

        List<MarketingPrize> allPrizes = prizeMapper.selectList(
                new LambdaQueryWrapper<MarketingPrize>()
                        .eq(MarketingPrize::getActivityId, activityId)
        );
        if (allPrizes == null || allPrizes.isEmpty()) {
            throw new IllegalArgumentException("活动缺少奖品配置，activityId=" + activityId);
        }

        List<PrizeRateInfo> rateInfoList = new ArrayList<>();
        int totalRate = 0;
        for (MarketingPrize prize : allPrizes) {
            Integer weight = prize.getWeight();
            if (weight == null || weight <= 0) continue;
            totalRate += weight;
            rateInfoList.add(new PrizeRateInfo(String.valueOf(prize.getId()), weight));
        }

        if (rateInfoList.isEmpty()) {
            throw new IllegalArgumentException("活动有效奖品为空，activityId=" + activityId);
        }
        if (totalRate > RATE_BASE) {
            throw new IllegalArgumentException("奖品权重和超过 " + RATE_BASE + "，实际为 " + totalRate);
        }

        clearRateAndStockOnly(activityId);

        IDrawAlgorithm algorithm = algorithmMap.get("redisHashDrawAlgorithm");
        if (algorithm == null) {
            throw new IllegalStateException("未找到 redisHashDrawAlgorithm 算法实现");
        }
        algorithm.initRateTuple(activityId, rateInfoList);

        for (MarketingPrize prize : allPrizes) {
            Integer stockVal = prize.getSurplusStock() != null ? prize.getSurplusStock() : prize.getTotalStock();
            if (stockVal == null || stockVal < 0) stockVal = 0;
            String stockKey = STOCK_KEY_PREFIX + activityId + ":" + prize.getId();
            stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(stockVal));
        }

        log.info("活动预热完成 activityId={}, 有效奖品数={}, 权重和={}",
                activityId, rateInfoList.size(), totalRate);
    }

    @Override
    public void clearPreheatCache(Long activityId) {
        if (activityId == null || activityId <= 0) {
            throw new IllegalArgumentException("activityId 非法");
        }
        clearRateAndStockOnly(activityId);
        deleteByPrefix(REQUEST_LOCK_KEY_PREFIX + activityId + ":");
        log.info("活动预热缓存已清理 activityId={}", activityId);
    }

    @Override
    public void preheatOnlineByMerchant(String merchantId) {
        if (merchantId == null || merchantId.isBlank()) {
            return;
        }
        List<MarketingActivity> activities = activityMapper.selectList(
                new LambdaQueryWrapper<MarketingActivity>()
                        .eq(MarketingActivity::getMerchantId, merchantId)
                        .eq(MarketingActivity::getStatus, 1)
                        .orderByAsc(MarketingActivity::getId)
                        .last("LIMIT 50")
        );
        if (activities == null || activities.isEmpty()) {
            log.info("merchant login preheat skipped, no online activities. merchantId={}", merchantId);
            return;
        }
        int success = 0;
        for (MarketingActivity activity : activities) {
            try {
                preheat(activity.getId());
                success++;
            } catch (Exception e) {
                log.warn("merchant login preheat failed, merchantId={}, activityId={}, err={}",
                        merchantId, activity.getId(), e.getMessage());
            }
        }
        log.info("merchant login preheat finished, merchantId={}, total={}, success={}",
                merchantId, activities.size(), success);
    }

    private void clearRateAndStockOnly(Long activityId) {
        stringRedisTemplate.delete(RATE_MAP_KEY_PREFIX + activityId);
        deleteByPrefix(STOCK_KEY_PREFIX + activityId + ":");
    }

    private void deleteByPrefix(String keyPrefix) {
        Set<String> keys = stringRedisTemplate.keys(keyPrefix + "*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }
}

