package com.yzh.draw.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DrawResponse {

    /** 抽奖是否成功（被拦截或系统异常时为 false） */
    private boolean success;

    /** 奖品ID，未中奖时可能为 NO_AWARD */
    private String prizeId;

    /** 奖品名称 */
    private String prizeName;

    /** 返回描述信息 */
    private String message;

    /** 扣减后的剩余积分（后端真值） */
    private BigDecimal remainPoints;
}