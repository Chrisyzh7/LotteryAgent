package com.yzh.model.vo;

import lombok.Data;

/**
 * 认证请求对象（用于登录、注册）
 */
@Data
public class AuthRequest {
    /** 登录账号 */
    private String username;

    /** 登录密码（明文，由后端加密校验） */
    private String password;
}
