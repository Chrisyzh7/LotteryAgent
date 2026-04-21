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
public class CUserRewardVO {

    private Long activityId;
    private String activityName;

    private Long prizeId;
    private String prizeName;

    private LocalDateTime createTime;
}

