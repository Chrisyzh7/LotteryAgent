package com.yzh.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户账户实体类，对应数据库 user_account 表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_account")
public class UserAccount {

    /** 自增ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号 (新增字段) */
    private String username;

    /** 登录密码（加密后） (新增字段) */
    private String password;

    /** 用户ID (与营销平台一致，全局唯一) */
    private String userId;

    /** 用户等级(0:普通, 1:会员) */
    private Integer userLevel;

    /** 大模型专属积分 */
    private java.math.BigDecimal modelPoints;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
