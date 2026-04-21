package com.yzh.service.impl;

import com.yzh.mapper.MarketingActivityMapper;
import com.yzh.mapper.MarketingPrizeMapper;
import com.yzh.model.entity.MarketingActivity;
import com.yzh.model.entity.MarketingPrize;
import com.yzh.model.vo.CreateMarketingActivityRequest;
import com.yzh.model.vo.CreateMarketingActivityResponse;
import com.yzh.model.vo.PrizeConfigRequest;
import com.yzh.service.MarketingActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MarketingActivityServiceImpl implements MarketingActivityService {

    private static final int MAX_RATE_BASE = 10000;
    private static final String DEFAULT_STYLE = "dark_neon";
    private static final BigDecimal DEFAULT_INITIAL_POINTS = new BigDecimal("100.00");

    private final MarketingActivityMapper marketingActivityMapper;
    private final MarketingPrizeMapper marketingPrizeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateMarketingActivityResponse createActivity(String merchantId, CreateMarketingActivityRequest request) {
        validateCreateRequest(merchantId, request);

        LocalDateTime now = LocalDateTime.now();
        BigDecimal initialPoints = request.getInitialUserPoints() == null ? DEFAULT_INITIAL_POINTS : request.getInitialUserPoints();

        MarketingActivity activity = MarketingActivity.builder()
                .merchantId(merchantId)
                .activityName(request.getActivityName().trim())
                .deductPoints(request.getDeductPoints() == null ? BigDecimal.ZERO : request.getDeductPoints())
                .initialUserPoints(initialPoints)
                .pageStyle(resolvePageStyle(request.getPageStyle()))
                .status(0)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .createTime(now)
                .updateTime(now)
                .build();
        marketingActivityMapper.insert(activity);

        Long activityId = activity.getId();
        for (PrizeConfigRequest prizeReq : request.getPrizes()) {
            int stock = prizeReq.getTotalStock() == null ? 0 : prizeReq.getTotalStock();
            int resolvedWeight = resolveWeight(prizeReq);
            MarketingPrize prize = MarketingPrize.builder()
                    .activityId(activityId)
                    .prizeName(prizeReq.getPrizeName().trim())
                    .weight(resolvedWeight)
                    .totalStock(stock)
                    .surplusStock(stock)
                    .prizeImage(prizeReq.getPrizeImage())
                    .createTime(now)
                    .updateTime(now)
                    .build();
            marketingPrizeMapper.insert(prize);
        }

        return CreateMarketingActivityResponse.builder()
                .activityId(activityId)
                .activityName(activity.getActivityName())
                .playLink("/lottery.html?activityId=" + activityId + "&style=" + activity.getPageStyle())
                .initialUserPoints(activity.getInitialUserPoints())
                .pageStyle(activity.getPageStyle())
                .publishHint("活动已创建为草稿态，请调用 /api/lottery/publish 发布并预热")
                .build();
    }

    private void validateCreateRequest(String merchantId, CreateMarketingActivityRequest request) {
        if (merchantId == null || merchantId.isBlank()) {
            throw new IllegalArgumentException("商户未登录或登录态失效");
        }
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (request.getActivityName() == null || request.getActivityName().isBlank()) {
            throw new IllegalArgumentException("activityName 不能为空");
        }
        if (request.getPrizes() == null || request.getPrizes().isEmpty()) {
            throw new IllegalArgumentException("prizes 不能为空");
        }
        if (request.getStartTime() != null && request.getEndTime() != null
                && request.getEndTime().isBefore(request.getStartTime())) {
            throw new IllegalArgumentException("结束时间不能早于开始时间");
        }
        if (request.getInitialUserPoints() != null && request.getInitialUserPoints().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("initialUserPoints 不能为负数");
        }

        int weightSum = 0;
        for (PrizeConfigRequest prize : request.getPrizes()) {
            if (prize.getPrizeName() == null || prize.getPrizeName().isBlank()) {
                throw new IllegalArgumentException("奖品名称不能为空");
            }
            if (prize.getTotalStock() == null || prize.getTotalStock() < 0) {
                throw new IllegalArgumentException("奖品库存不能为负数");
            }
            weightSum += resolveWeight(prize);
        }

        if (weightSum > MAX_RATE_BASE) {
            throw new IllegalArgumentException("奖品总权重不能超过 " + MAX_RATE_BASE + "，当前为 " + weightSum);
        }
    }

    private int resolveWeight(PrizeConfigRequest prize) {
        if (prize.getWeight() != null && prize.getWeight() > 0) {
            return prize.getWeight();
        }
        if (prize.getProbability() != null) {
            double p = prize.getProbability();
            if (p > 0 && p <= 1) {
                int converted = (int) Math.round(p * MAX_RATE_BASE);
                return Math.max(converted, 1);
            }
            throw new IllegalArgumentException("probability 必须在 (0,1] 区间");
        }
        return 1;
    }

    private String resolvePageStyle(String style) {
        if ("ins_minimal".equals(style) || "fresh_light".equals(style) || "dark_neon".equals(style)) {
            return style;
        }
        return DEFAULT_STYLE;
    }
}