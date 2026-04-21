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
@TableName("user_rewards")
public class UserReward {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private String cUserId;

    private Long prizeId;

    private String requestId;

    /** 0:待发奖 1:成功 2:失败 */
    private Integer awardState;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

