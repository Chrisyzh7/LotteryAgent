package com.yzh.model.vo;

import lombok.Data;

@Data
public class UpdateRewardStateRequest {

    private Long rewardId;

    /**
     * 1-已发放 2-发放失败
     */
    private Integer awardState;
}

