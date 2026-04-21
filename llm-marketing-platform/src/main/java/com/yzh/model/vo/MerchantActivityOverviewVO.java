package com.yzh.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantActivityOverviewVO {
    private Long activityId;
    private String activityName;
    private Integer status;
    private String pageStyle;
    private BigDecimal deductPoints;
    private LocalDateTime createTime;

    private Integer prizeCount;
    private Long totalStock;
    private Long surplusStock;
    private Long rewardCount;
    private Long pendingRewardCount;

    private String playLink;
    private String manageLink;
}

