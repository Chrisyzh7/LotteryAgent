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
@TableName("c_user")
public class CUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String cUserId;

    private String username;

    private String passwordHash;

    private String nickname;

    private String mobile;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
