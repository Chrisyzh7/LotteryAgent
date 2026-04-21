package com.yzh.model.vo;

import lombok.Data;

@Data
public class PrizeConfigRequest {

    private String prizeName;

    /** 0:谢谢参与 1:实物 2:积分 3:虚拟券 */
    private Integer prizeType;

    private Integer totalStock;

    /** 权重（总和建议 <= 10000） */
    private Integer weight;

    /** 概率（0~1），如果传了且未传 weight，会自动换算为权重 */
    private Double probability;

    private String prizeImage;
}
