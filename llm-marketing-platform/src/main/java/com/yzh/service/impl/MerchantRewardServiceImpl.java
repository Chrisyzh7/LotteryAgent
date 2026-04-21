package com.yzh.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzh.mapper.CUserMapper;
import com.yzh.mapper.MarketingActivityMapper;
import com.yzh.mapper.MarketingPrizeMapper;
import com.yzh.mapper.UserRewardMapper;
import com.yzh.model.entity.CUser;
import com.yzh.model.entity.MarketingActivity;
import com.yzh.model.entity.MarketingPrize;
import com.yzh.model.entity.UserReward;
import com.yzh.model.vo.MerchantRewardRecordVO;
import com.yzh.service.MerchantRewardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MerchantRewardServiceImpl implements MerchantRewardService {

    private final UserRewardMapper userRewardMapper;
    private final MarketingActivityMapper marketingActivityMapper;
    private final MarketingPrizeMapper marketingPrizeMapper;
    private final CUserMapper cUserMapper;

    @Override
    public List<MerchantRewardRecordVO> listRecords(Long activityId, String merchantId) {
        MarketingActivity activity = assertOwnedActivity(activityId, merchantId);

        List<UserReward> rewards = userRewardMapper.selectList(
                new LambdaQueryWrapper<UserReward>()
                        .eq(UserReward::getActivityId, activity.getId())
                        .orderByDesc(UserReward::getCreateTime)
                        .last("LIMIT 500")
        );

        if (rewards.isEmpty()) {
            return List.of();
        }

        List<Long> prizeIds = rewards.stream()
                .map(UserReward::getPrizeId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, String> prizeNameMap = new HashMap<>();
        if (!prizeIds.isEmpty()) {
            List<MarketingPrize> prizes = marketingPrizeMapper.selectBatchIds(prizeIds);
            for (MarketingPrize p : prizes) {
                prizeNameMap.put(p.getId(), p.getPrizeName());
            }
        }

        List<String> cUserIds = rewards.stream()
                .map(UserReward::getCUserId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(Collectors.toList());

        Map<String, CUser> userMap = new HashMap<>();
        if (!cUserIds.isEmpty()) {
            List<CUser> users = cUserMapper.selectList(
                    new LambdaQueryWrapper<CUser>().in(CUser::getCUserId, cUserIds)
            );
            for (CUser u : users) {
                userMap.put(u.getCUserId(), u);
            }
        }

        return rewards.stream().map(r -> {
            CUser user = userMap.get(r.getCUserId());
            return MerchantRewardRecordVO.builder()
                    .rewardId(r.getId())
                    .activityId(r.getActivityId())
                    .requestId(r.getRequestId())
                    .cUserId(r.getCUserId())
                    .username(user != null ? user.getUsername() : null)
                    .nickname(user != null ? user.getNickname() : "未知用户")
                    .mobile(user != null ? user.getMobile() : null)
                    .prizeId(r.getPrizeId())
                    .prizeName(r.getPrizeId() == null ? "谢谢参与" : prizeNameMap.getOrDefault(r.getPrizeId(), "未知奖品"))
                    .awardState(r.getAwardState())
                    .createTime(r.getCreateTime())
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateState(Long rewardId, Integer awardState, String merchantId) {
        if (rewardId == null || rewardId <= 0) {
            throw new IllegalArgumentException("rewardId 非法");
        }
        if (awardState == null || (awardState != 1 && awardState != 2)) {
            throw new IllegalArgumentException("awardState 仅支持 1(已发放)/2(发放失败)");
        }

        UserReward reward = userRewardMapper.selectById(rewardId);
        if (reward == null) {
            throw new IllegalArgumentException("奖励记录不存在");
        }

        assertOwnedActivity(reward.getActivityId(), merchantId);

        reward.setAwardState(awardState);
        reward.setUpdateTime(LocalDateTime.now());
        userRewardMapper.updateById(reward);
    }

    private MarketingActivity assertOwnedActivity(Long activityId, String merchantId) {
        if (activityId == null || activityId <= 0) {
            throw new IllegalArgumentException("activityId 非法");
        }
        if (merchantId == null || merchantId.isBlank()) {
            throw new IllegalArgumentException("商户未登录");
        }
        MarketingActivity activity = marketingActivityMapper.selectById(activityId);
        if (activity == null) {
            throw new IllegalArgumentException("活动不存在");
        }
        if (!merchantId.equals(activity.getMerchantId())) {
            throw new IllegalArgumentException("无权限操作该活动");
        }
        return activity;
    }
}
