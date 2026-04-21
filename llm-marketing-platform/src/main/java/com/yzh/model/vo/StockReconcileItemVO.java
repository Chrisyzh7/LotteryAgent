package com.yzh.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReconcileItemVO {
    private Long prizeId;
    private String prizeName;
    private Integer mysqlStock;
    private Integer redisStock;
    private Integer diff;
    /**
     * MISSING_REDIS / INVALID_REDIS / VALUE_MISMATCH / ORPHAN_REDIS_KEY
     */
    private String issueType;
    private String redisKey;
}

