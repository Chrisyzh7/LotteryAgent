package com.yzh.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzh.mapper.CUserMapper;
import com.yzh.mapper.CUserPointsMapper;
import com.yzh.mapper.MarketingActivityMapper;
import com.yzh.model.entity.CUser;
import com.yzh.model.entity.CUserPoints;
import com.yzh.model.entity.MarketingActivity;
import com.yzh.model.vo.CUserPointsVO;
import com.yzh.service.CUserPointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CUserPointsServiceImpl implements CUserPointsService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal DEFAULT_INITIAL_POINTS = new BigDecimal("100.00");

    private final CUserPointsMapper cUserPointsMapper;
    private final MarketingActivityMapper marketingActivityMapper;
    private final CUserMapper cUserMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CUserPointsVO ensureAndGet(Long activityId, String cUserId) {
        if (activityId == null || activityId <= 0) {
            throw new IllegalArgumentException("activityId 非法");
        }
        if (cUserId == null || cUserId.isBlank()) {
            throw new IllegalArgumentException("cUserId 不能为空");
        }

        MarketingActivity activity = marketingActivityMapper.selectById(activityId);
        if (activity == null) {
            throw new IllegalArgumentException("活动不存在");
        }

        CUser user = cUserMapper.selectOne(new LambdaQueryWrapper<CUser>().eq(CUser::getCUserId, cUserId));
        if (user == null) {
            throw new IllegalArgumentException("用户不存在或已失效，请重新登录");
        }

        CUserPoints points = cUserPointsMapper.selectOne(
                new LambdaQueryWrapper<CUserPoints>()
                        .eq(CUserPoints::getActivityId, activityId)
                        .eq(CUserPoints::getCUserId, cUserId)
                        .last("LIMIT 1")
        );

        if (points == null) {
            BigDecimal initialPoints = activity.getInitialUserPoints() == null ? DEFAULT_INITIAL_POINTS : activity.getInitialUserPoints();
            CUserPoints insert = CUserPoints.builder()
                    .activityId(activityId)
                    .cUserId(cUserId)
                    .totalPoints(initialPoints)
                    .usedPoints(ZERO)
                    .remainPoints(initialPoints)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            try {
                cUserPointsMapper.insert(insert);
                points = insert;
            } catch (DuplicateKeyException e) {
                points = cUserPointsMapper.selectOne(
                        new LambdaQueryWrapper<CUserPoints>()
                                .eq(CUserPoints::getActivityId, activityId)
                                .eq(CUserPoints::getCUserId, cUserId)
                                .last("LIMIT 1")
                );
            }
        }

        return CUserPointsVO.builder()
                .activityId(activityId)
                .cUserId(cUserId)
                .username(user.getUsername())
                .nickname(user.getNickname())
                .mobile(user.getMobile())
                .totalPoints(points.getTotalPoints())
                .usedPoints(points.getUsedPoints())
                .remainPoints(points.getRemainPoints())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal deductForDraw(Long activityId, String cUserId, BigDecimal cost) {
        BigDecimal normalizedCost = normalizePoints(cost);
        CUserPointsVO points = ensureAndGet(activityId, cUserId);
        if (normalizedCost.compareTo(ZERO) <= 0) {
            return points.getRemainPoints();
        }

        int updated = cUserPointsMapper.deductForDraw(activityId, cUserId, normalizedCost);
        if (updated <= 0) {
            throw new IllegalArgumentException("积分不足");
        }
        return ensureAndGet(activityId, cUserId).getRemainPoints();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal refund(Long activityId, String cUserId, BigDecimal points) {
        BigDecimal normalized = normalizePoints(points);
        if (normalized.compareTo(ZERO) <= 0) {
            return ensureAndGet(activityId, cUserId).getRemainPoints();
        }
        cUserPointsMapper.refund(activityId, cUserId, normalized);
        return ensureAndGet(activityId, cUserId).getRemainPoints();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal grantToOne(Long activityId, String cUserId, BigDecimal points, String merchantId) {
        MarketingActivity activity = assertMerchantOwnedActivity(activityId, merchantId);
        BigDecimal normalized = normalizePositive(points);

        ensureAndGet(activity.getId(), cUserId);
        int updated = cUserPointsMapper.grantToOne(activityId, cUserId, normalized);
        if (updated <= 0) {
            throw new IllegalStateException("积分发放失败");
        }
        return ensureAndGet(activityId, cUserId).getRemainPoints();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int grantToAll(Long activityId, BigDecimal points, boolean includeFutureUsers, String merchantId) {
        MarketingActivity activity = assertMerchantOwnedActivity(activityId, merchantId);
        BigDecimal normalized = normalizePositive(points);

        int affected = cUserPointsMapper.grantToAll(activityId, normalized);
        if (includeFutureUsers) {
            BigDecimal initial = activity.getInitialUserPoints() == null ? DEFAULT_INITIAL_POINTS : activity.getInitialUserPoints();
            activity.setInitialUserPoints(initial.add(normalized));
            activity.setUpdateTime(LocalDateTime.now());
            marketingActivityMapper.updateById(activity);
        }
        return affected;
    }

    private MarketingActivity assertMerchantOwnedActivity(Long activityId, String merchantId) {
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

    private BigDecimal normalizePoints(BigDecimal points) {
        if (points == null) return ZERO;
        return points.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizePositive(BigDecimal points) {
        BigDecimal normalized = normalizePoints(points);
        if (normalized.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("points 必须大于 0");
        }
        return normalized;
    }
}
