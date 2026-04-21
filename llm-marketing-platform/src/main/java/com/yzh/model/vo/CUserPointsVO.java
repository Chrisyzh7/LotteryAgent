package com.yzh.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CUserPointsVO {

    private Long activityId;

    private String cUserId;

    private String username;

    private String nickname;

    private String mobile;

    private BigDecimal totalPoints;

    private BigDecimal usedPoints;

    private BigDecimal remainPoints;
}
