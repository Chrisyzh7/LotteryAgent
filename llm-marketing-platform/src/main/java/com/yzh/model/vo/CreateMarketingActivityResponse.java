package com.yzh.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMarketingActivityResponse {

    private Long activityId;

    private String activityName;

    private String playLink;

    private java.math.BigDecimal initialUserPoints;

    private String pageStyle;

    private String publishHint;
}
