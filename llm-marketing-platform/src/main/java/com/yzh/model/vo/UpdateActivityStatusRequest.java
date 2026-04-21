package com.yzh.model.vo;

import lombok.Data;

@Data
public class UpdateActivityStatusRequest {
    private Long activityId;
    /**
     * publish / stop
     */
    private String action;
}

