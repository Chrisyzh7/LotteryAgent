package com.yzh.draw.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 奖品概率配置模型
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PrizeRateInfo {
    
    /** 奖品ID */
    private String prizeId;
    
    /**
     * 奖品分配的概率值/比重份额
     * 在 O(1) 算法中，代表占总分母（如10000）的份数。 示例：50代表 0.5%
     * 在前缀和算法中，代表该区间累加后的上界值。
     */
    private Integer awardRate;
}
