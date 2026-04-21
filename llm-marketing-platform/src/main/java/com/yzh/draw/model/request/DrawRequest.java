package com.yzh.draw.model.request;

import lombok.Data;

@Data
public class DrawRequest {
    /** 会员ID */
    private String userId;
    /** 参与的活动ID */
    private Long activityId;
    /** 
     * 请求流水号，用于 Redis 分布式锁防重和防超发
     * 建议生成格式：UUID 或者 userId + timestamp 等 
     */
    private String requestId;
}
