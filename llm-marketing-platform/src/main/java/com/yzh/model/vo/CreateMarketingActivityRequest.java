package com.yzh.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreateMarketingActivityRequest {

    private String activityName;

    private BigDecimal deductPoints;

    /** 用户进入活动时的初始积分，不传默认 100 */
    private BigDecimal initialUserPoints;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 页面风格：dark_neon / ins_minimal / fresh_light */
    private String pageStyle;

    private List<PrizeConfigRequest> prizes;
}
