package com.yzh.model.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MerchantGrantPointsRequest {

    private Long activityId;

    /**
     * 指定用户加积分时必填；全体加积分时可不传
     */
    private String cUserId;

    /**
     * User input in single-grant mode. Supports username/mobile/cUserId/dbId.
     */
    private String userRef;

    /**
     * 加积分数值，必须 > 0
     */
    private BigDecimal points;

    /**
     * true: 给活动所有已入场用户加积分
     * false: 仅给指定 cUserId 加积分
     */
    private Boolean applyToAll;

    /**
     * 仅 applyToAll=true 时生效：
     * 是否同步增加活动默认初始积分，让后续新用户也自动获得这次加分
     */
    private Boolean includeFutureUsers;
}
