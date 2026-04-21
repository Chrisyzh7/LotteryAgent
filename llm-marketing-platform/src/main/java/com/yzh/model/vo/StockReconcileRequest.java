package com.yzh.model.vo;

import lombok.Data;

@Data
public class StockReconcileRequest {
    private Long activityId;
    /**
     * CHECK / REPAIR
     */
    private String mode;
}

