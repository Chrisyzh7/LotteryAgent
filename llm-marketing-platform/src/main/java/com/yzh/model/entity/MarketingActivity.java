package com.yzh.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("marketing_activity")
public class MarketingActivity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String merchantId;

    private String activityName;

    private BigDecimal deductPoints;

    /** 用户进入活动时的初始积分 */
    private BigDecimal initialUserPoints;

    /** 页面风格：dark_neon / ins_minimal / fresh_light */
    private String pageStyle;

    /** 0:草稿/下线 1:上线 2:结束 */
    private Integer status;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}