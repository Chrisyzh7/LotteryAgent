package com.yzh.controller;

import com.yzh.model.vo.AuthRequest;
import com.yzh.model.vo.ChatResponse;
import com.yzh.model.vo.TokenVO;
import com.yzh.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户注册登录接口入口
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 账号注册
     */
    @PostMapping("/register")
    public ChatResponse<String> register(@RequestBody AuthRequest request) {
        if (request.getUsername() == null || request.getPassword() == null) {
            return ChatResponse.error("账号和密码不能为空");
        }
        try {
            userService.register(request);
            return ChatResponse.success("注册成功");
        } catch (Exception e) {
            return ChatResponse.error(e.getMessage());
        }
    }

    /**
     * 账号登录，返回 Token
     */
    @PostMapping("/login")
    public ChatResponse<TokenVO> login(@RequestBody AuthRequest request) {
        if (request.getUsername() == null || request.getPassword() == null) {
            return ChatResponse.error("账号和密码不能为空");
        }
        try {
            TokenVO tokenVO = userService.login(request);
            return ChatResponse.success(tokenVO);
        } catch (Exception e) {
            return ChatResponse.error(e.getMessage());
        }
    }
}
