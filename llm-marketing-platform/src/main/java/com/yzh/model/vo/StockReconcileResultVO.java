package com.yzh.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReconcileResultVO {
    private Long activityId;
    private String mode;
    private Boolean repaired;
    private Boolean rateMapExists;
    private Integer checkedPrizeCount;
    private Integer mismatchCount;
    private List<StockReconcileItemVO> mismatches;
    private String message;
}

