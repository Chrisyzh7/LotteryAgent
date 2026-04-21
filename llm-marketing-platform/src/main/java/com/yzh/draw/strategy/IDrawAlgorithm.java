package com.yzh.draw.strategy;

import com.yzh.draw.model.PrizeRateInfo;

import java.util.List;

/**
 * 核心抽选算法接口 (策略模式抽象)
 */
public interface IDrawAlgorithm {

    /**
     * 系统启动或活动预热时，将配置的概率数据结构进行装配初始化
     *
     * @param activityId 活动ID
     * @param prizeList  奖品及对应分配策略列表的原始数据
     */
    void initRateTuple(Long activityId, List<PrizeRateInfo> prizeList);

    /**
     * 判断某活动的缓存散列数据是否已被装载过了
     *
     * @param activityId 活动ID
     * @return boolean
     */
    boolean isExist(Long activityId);

    /**
     * 根据不同的算法具体实现进行随机中奖计算
     *
     * @param activityId 活动ID
     * @return 返回抽中的奖品ID，没中奖返回 "NO_AWARD"
     */
    String randomDraw(Long activityId);
}
