package com.yzh.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录成功后返回给前端的 Token 信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenVO {

    /** JWT Token 字符串 */
    private String token;

    /** 用户ID */
    private String userId;

    /** 账号名 */
    private String username;
}
