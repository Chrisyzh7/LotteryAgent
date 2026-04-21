package com.yzh.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzh.mapper.CUserMapper;
import com.yzh.mapper.MarketingActivityMapper;
import com.yzh.mapper.MarketingPrizeMapper;
import com.yzh.mapper.UserRewardMapper;
import com.yzh.model.entity.CUser;
import com.yzh.model.entity.MarketingActivity;
import com.yzh.model.entity.MarketingPrize;
import com.yzh.model.entity.UserReward;
import com.yzh.model.vo.CUserAuthRequest;
import com.yzh.model.vo.CUserAuthResponse;
import com.yzh.model.vo.CUserPointsVO;
import com.yzh.model.vo.CUserRewardVO;
import com.yzh.service.CUserPointsService;
import com.yzh.service.CUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CUserServiceImpl implements CUserService {

    private final CUserMapper cUserMapper;
    private final UserRewardMapper userRewardMapper;
    private final MarketingActivityMapper marketingActivityMapper;
    private final MarketingPrizeMapper marketingPrizeMapper;
    private final CUserPointsService cUserPointsService;

    @Override
    public CUserAuthResponse auth(CUserAuthRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }

        String username = normalize(request.getUsername());
        String password = request.getPassword() == null ? "" : request.getPassword();
        String nickname = normalize(request.getNickname());
        String mobile = normalize(request.getMobile());

        if (username == null) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (password.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }

        CUser user = cUserMapper.selectOne(
                new LambdaQueryWrapper<CUser>()
                        .eq(CUser::getUsername, username)
                        .last("LIMIT 1")
        );

        boolean newUser = false;
        LocalDateTime now = LocalDateTime.now();

        if (user == null) {
            CUser legacy = null;
            String cUserId = normalize(request.getCUserId());
            if (cUserId != null) {
                legacy = cUserMapper.selectOne(
                        new LambdaQueryWrapper<CUser>()
                                .eq(CUser::getCUserId, cUserId)
                                .last("LIMIT 1")
                );
            }

            if (legacy != null && isBlank(legacy.getUsername())) {
                String finalMobile = mobile != null ? mobile : normalize(legacy.getMobile());
                if (finalMobile == null) {
                    throw new IllegalArgumentException("请先补充手机号（用于发奖联系）");
                }
                assertMobileAvailable(finalMobile, legacy.getId());
                legacy.setUsername(username);
                legacy.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
                if (nickname != null) {
                    legacy.setNickname(nickname);
                } else if (isBlank(legacy.getNickname())) {
                    legacy.setNickname(username);
                }
                legacy.setMobile(finalMobile);
                legacy.setUpdateTime(now);
                cUserMapper.updateById(legacy);
                user = legacy;
                newUser = true;
            } else {
                if (mobile == null) {
                    throw new IllegalArgumentException("首次注册请填写手机号（用于发奖联系）");
                }
                assertMobileAvailable(mobile, null);
                user = CUser.builder()
                        .cUserId("c_" + IdUtil.fastSimpleUUID())
                        .username(username)
                        .passwordHash(BCrypt.hashpw(password, BCrypt.gensalt()))
                        .nickname(nickname != null ? nickname : username)
                        .mobile(mobile)
                        .createTime(now)
                        .updateTime(now)
                        .build();
                cUserMapper.insert(user);
                newUser = true;
            }
        } else {
            String passwordHash = user.getPasswordHash();
            if (isBlank(passwordHash) || !BCrypt.checkpw(password, passwordHash)) {
                throw new IllegalArgumentException("用户名或密码错误");
            }

            boolean changed = false;
            if (nickname != null && !nickname.equals(user.getNickname())) {
                user.setNickname(nickname);
                changed = true;
            }
            if (mobile != null && !mobile.equals(user.getMobile())) {
                assertMobileAvailable(mobile, user.getId());
                user.setMobile(mobile);
                changed = true;
            }
            if (changed) {
                user.setUpdateTime(now);
                cUserMapper.updateById(user);
            }
        }

        CUserPointsVO pointsVO = null;
        if (request.getActivityId() != null && request.getActivityId() > 0) {
            pointsVO = cUserPointsService.ensureAndGet(request.getActivityId(), user.getCUserId());
        }

        return CUserAuthResponse.builder()
                .cUserId(user.getCUserId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .mobile(user.getMobile())
                .remainPoints(pointsVO != null ? pointsVO.getRemainPoints() : null)
                .newUser(newUser)
                .loginMessage(newUser ? "注册成功，已进入活动" : "登录成功，欢迎回来")
                .build();
    }

    @Override
    public void ensureExists(String cUserId) {
        if (cUserId == null || cUserId.isBlank()) {
            throw new IllegalArgumentException("cUserId 不能为空");
        }
        CUser user = cUserMapper.selectOne(new LambdaQueryWrapper<CUser>().eq(CUser::getCUserId, cUserId));
        if (user == null) {
            throw new IllegalArgumentException("用户不存在或已失效，请重新登录");
        }
    }

    @Override
    public CUser resolveByUserRef(String userRef) {
        String ref = normalize(userRef);
        if (ref == null) {
            throw new IllegalArgumentException("用户标识不能为空");
        }

        CUser byCUserId = cUserMapper.selectOne(
                new LambdaQueryWrapper<CUser>()
                        .eq(CUser::getCUserId, ref)
                        .last("LIMIT 1")
        );
        if (byCUserId != null) {
            return byCUserId;
        }

        CUser byUsername = cUserMapper.selectOne(
                new LambdaQueryWrapper<CUser>()
                        .eq(CUser::getUsername, ref)
                        .last("LIMIT 1")
        );
        if (byUsername != null) {
            return byUsername;
        }

        CUser byMobile = cUserMapper.selectOne(
                new LambdaQueryWrapper<CUser>()
                        .eq(CUser::getMobile, ref)
                        .last("LIMIT 1")
        );
        if (byMobile != null) {
            return byMobile;
        }

        if (ref.matches("\\d+")) {
            CUser byId = cUserMapper.selectById(Long.parseLong(ref));
            if (byId != null) {
                return byId;
            }
        }

        List<CUser> byNicknameList = cUserMapper.selectList(
                new LambdaQueryWrapper<CUser>()
                        .eq(CUser::getNickname, ref)
        );
        if (byNicknameList.size() == 1) {
            return byNicknameList.get(0);
        }
        if (byNicknameList.size() > 1) {
            throw new IllegalArgumentException("该昵称匹配到多个用户，请改用手机号或cUserId");
        }

        throw new IllegalArgumentException("未找到用户，请输入用户名/手机号/cUserId");
    }

    @Override
    public CUserPointsVO getPoints(Long activityId, String cUserId) {
        return cUserPointsService.ensureAndGet(activityId, cUserId);
    }

    @Override
    public List<CUserRewardVO> listRewards(String cUserId) {
        if (cUserId == null || cUserId.isBlank()) {
            throw new IllegalArgumentException("cUserId 不能为空");
        }

        List<UserReward> rewards = userRewardMapper.selectList(
                new LambdaQueryWrapper<UserReward>()
                        .eq(UserReward::getCUserId, cUserId)
                        .orderByDesc(UserReward::getCreateTime)
                        .last("LIMIT 50")
        );

        List<CUserRewardVO> result = new ArrayList<>();
        for (UserReward reward : rewards) {
            MarketingActivity activity = marketingActivityMapper.selectById(reward.getActivityId());
            MarketingPrize prize = reward.getPrizeId() != null ? marketingPrizeMapper.selectById(reward.getPrizeId()) : null;
            result.add(CUserRewardVO.builder()
                    .activityId(reward.getActivityId())
                    .activityName(activity != null ? activity.getActivityName() : "未知活动")
                    .prizeId(reward.getPrizeId())
                    .prizeName(prize != null ? prize.getPrizeName() : "未知奖品")
                    .createTime(reward.getCreateTime())
                    .build());
        }
        return result;
    }

    private void assertMobileAvailable(String mobile, Long currentUserId) {
        if (mobile == null) {
            return;
        }
        CUser sameMobile = cUserMapper.selectOne(
                new LambdaQueryWrapper<CUser>()
                        .eq(CUser::getMobile, mobile)
                        .last("LIMIT 1")
        );
        if (sameMobile != null && (currentUserId == null || !sameMobile.getId().equals(currentUserId))) {
            throw new IllegalArgumentException("该手机号已绑定其他账号");
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
