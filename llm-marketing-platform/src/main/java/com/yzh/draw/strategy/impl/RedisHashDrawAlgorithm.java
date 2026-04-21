package com.yzh.draw.strategy.impl;

import com.yzh.draw.model.PrizeRateInfo;
import com.yzh.draw.strategy.IDrawAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 【中规中矩的活动适用】 O(1) 单次查询极大降低应用层计算压力
 * 空间换时间：使用 Redis Hash 存储随机数（1~10000）到奖品 ID 的散列映射
 */
@Slf4j
@Component("redisHashDrawAlgorithm")
public class RedisHashDrawAlgorithm implements IDrawAlgorithm {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 默认分发基数为一万（代表最小精度支持到万分之一）
    private final int RATE_BASE = 10000;
    private final String KEY_PREFIX = "lottery_map:";

    @Override
    public void initRateTuple(Long activityId, List<PrizeRateInfo> prizeList) {
        String key = KEY_PREFIX + activityId;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            return;
        }

        // 1. 初始化数组并将坑位放满对应的奖品ID
        List<String> mappingArray = new ArrayList<>(RATE_BASE);
        for (PrizeRateInfo info : prizeList) {
            int rateCount = info.getAwardRate(); // 例如这里传的是 10，则表示 10 份
            for (int i = 0; i < rateCount; i++) {
                mappingArray.add(info.getPrizeId());
            }
        }

        // 2. 将非中奖区间填补为 NO_AWARD
        int remain = RATE_BASE - mappingArray.size();
        for (int i = 0; i < remain; i++) {
            mappingArray.add("NO_AWARD");
        }

        // 3. 乱序保证公平分布
        Collections.shuffle(mappingArray);

        // 4. 将长度为 10000 的数组散列写入 Redis Hash 这个大散列表中，利用 Field 单次获取 O(1)
        for (int i = 0; i < mappingArray.size(); i++) {
            stringRedisTemplate.opsForHash().put(key, String.valueOf(i + 1), mappingArray.get(i));
        }

        log.info("活动 {} => Redis O(1) Map Hash 算法初始化完成", activityId);
    }

    @Override
    public boolean isExist(Long activityId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(KEY_PREFIX + activityId));
    }

    @Override
    public String randomDraw(Long activityId) {
        // 生成 1~10000 随机数
        int randomVal = new SecureRandom().nextInt(RATE_BASE) + 1;
        
        // 算法精髓：直接向 Redis 拿一个指定的 Hash key，不用做任何比较计算。
        Object prizeId = stringRedisTemplate.opsForHash().get(KEY_PREFIX + activityId, String.valueOf(randomVal));
        return prizeId != null ? prizeId.toString() : "NO_AWARD";
    }
}
