package com.yzh.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.jwt.JWT;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzh.mapper.SysUserMapper;
import com.yzh.mapper.UserAccountMapper;
import com.yzh.model.entity.SysUser;
import com.yzh.model.entity.UserAccount;
import com.yzh.model.vo.AuthRequest;
import com.yzh.model.vo.TokenVO;
import com.yzh.service.MerchantPreheatAsyncService;
import com.yzh.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserAccountMapper userAccountMapper;
    private final SysUserMapper sysUserMapper;
    private final MerchantPreheatAsyncService merchantPreheatAsyncService;

    public static final String JWT_SECRET = "llm-marketing-secret-key-2026";

    @Override
    public void register(AuthRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();

        Long count = userAccountMapper.selectCount(
                new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, username)
        );
        if (count > 0) {
            throw new RuntimeException("用户名已存在");
        }

        String hashedPwd = BCrypt.hashpw(password, BCrypt.gensalt());
        String userId = IdUtil.fastSimpleUUID();

        UserAccount newUser = UserAccount.builder()
                .username(username)
                .password(hashedPwd)
                .userId(userId)
                .userLevel(0)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        userAccountMapper.insert(newUser);

        ensureSysUser(username, hashedPwd);
        log.info("[UserService] register success username={}, userId={}", username, userId);
    }

    @Override
    public TokenVO login(AuthRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();

        UserAccount user = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, username)
        );
        if (user == null) {
            throw new RuntimeException("用户不存在，请先注册");
        }

        boolean isMatch = BCrypt.checkpw(password, user.getPassword());
        if (!isMatch) {
            throw new RuntimeException("密码错误");
        }

        ensureSysUser(user.getUsername(), user.getPassword());

        String token = JWT.create()
                .setPayload("userId", user.getUserId())
                .setPayload("username", user.getUsername())
                .setPayload("expireInfo", System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 7)
                .setKey(JWT_SECRET.getBytes())
                .sign();

        // Login should return quickly. Preheat runs in background.
        merchantPreheatAsyncService.preheatOnlineActivitiesAsync(user.getUsername());

        log.info("[UserService] login success username={}", username);

        return TokenVO.builder()
                .token(token)
                .userId(user.getUserId())
                .username(user.getUsername())
                .build();
    }

    private void ensureSysUser(String username, String encryptedPassword) {
        SysUser existed = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username).last("LIMIT 1")
        );
        if (existed != null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        SysUser sysUser = SysUser.builder()
                .merchantId(username)
                .username(username)
                .password(encryptedPassword)
                .createTime(now)
                .updateTime(now)
                .build();
        sysUserMapper.insert(sysUser);
    }
}