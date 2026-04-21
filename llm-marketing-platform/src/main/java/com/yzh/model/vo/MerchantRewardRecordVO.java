package com.yzh.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantRewardRecordVO {

    private Long rewardId;

    private Long activityId;

    private String requestId;

    private String cUserId;

    private String username;

    private String nickname;

    private String mobile;

    private Long prizeId;

    private String prizeName;

    /**
     * 0-待发放 1-已发放 2-发放失败
     */
    private Integer awardState;

    private LocalDateTime createTime;
}
