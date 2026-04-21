package com.yzh.draw.strategy.impl;

import com.yzh.draw.model.PrizeRateInfo;
import com.yzh.draw.strategy.IDrawAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 【极端概率或千万级奖池活动适用】 O(logN) 区间前缀和二分查找
 * 使用二分法将计算任务下压到应用层，避免给 Redis 网络产生 Big Key 导致阻塞
 */
@Slf4j
@Component("binarySearchDrawAlgorithm")
public class BinarySearchDrawAlgorithm implements IDrawAlgorithm {

    // 实际项目中可使用 Caffeine 等本地缓存替换 ConcurrentHashMap 进一步增加稳定性
    private final Map<Long, List<PrizeRateInfo>> preSumIntervalMap = new ConcurrentHashMap<>();

    // 假设极大部分情况的抽选基数扩展到千万甚至亿
    private final int RATE_BASE = 10000000;

    @Override
    public void initRateTuple(Long activityId, List<PrizeRateInfo> prizeList) {
        if (preSumIntervalMap.containsKey(activityId)) {
            return;
        }

        List<PrizeRateInfo> intervals = new ArrayList<>();
        int currentPrefix = 0;

        // 构造前缀和数组。比如 A: 5份，B: 10份 -> 则 A占据 [0, 5), B占据 [5, 15)
        for (PrizeRateInfo info : prizeList) {
            int rateCount = info.getAwardRate(); 
            // 存入的是区间上界
            intervals.add(new PrizeRateInfo(info.getPrizeId(), currentPrefix + rateCount));
            currentPrefix += rateCount;
        }

        preSumIntervalMap.put(activityId, intervals);
        log.info("活动 {} => 内存 O(log N) 区间二分算法初始化完成", activityId);
    }

    @Override
    public boolean isExist(Long activityId) {
        return preSumIntervalMap.containsKey(activityId);
    }

    @Override
    public String randomDraw(Long activityId) {
        List<PrizeRateInfo> intervals = preSumIntervalMap.get(activityId);
        if (intervals == null || intervals.isEmpty()) return "NO_AWARD";

        int randomVal = new SecureRandom().nextInt(RATE_BASE);

        // 二分查找
        int left = 0;
        int right = intervals.size() - 1;

        while (left <= right) {
            int mid = left + (right - left) / 2;
            int preSum = intervals.get(mid).getAwardRate();
            
            // 随机数在上界之内，可能是本区间
            // 注意：preSum 存的是独占上边界（不包含），所以如果 preSum <= randomVal，说明范围在其右侧
            if (preSum <= randomVal) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        } // 结尾：当 left 停止时，如果它没有越出边界，就说明落在该处

        // 由于剩余没覆盖到一千万范围的均视为未中奖
        if (left < intervals.size()) {
            return intervals.get(left).getPrizeId();
        }

        return "NO_AWARD";
    }
}
