package com.yzh.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("marketing_prizes")
public class MarketingPrize {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private String prizeName;

    /** 0:谢谢参与 1:实物 2:积分 3:虚拟券 */
    private Integer prizeType;

    private Integer weight;

    private Integer totalStock;

    private Integer surplusStock;

    private String prizeImage;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

