package com.yzh.service;

import com.yzh.model.vo.AuthRequest;
import com.yzh.model.vo.TokenVO;

/**
 * 账号注册与登录服务接口
 */
public interface UserService {

    /**
     * 注册账号
     */
    void register(AuthRequest request);

    /**
     * 登录并返回 Token
     */
    TokenVO login(AuthRequest request);
}
