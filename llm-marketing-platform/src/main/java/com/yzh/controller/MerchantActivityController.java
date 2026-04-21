package com.yzh.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzh.mapper.MarketingActivityMapper;
import com.yzh.mapper.MarketingPrizeMapper;
import com.yzh.mapper.UserRewardMapper;
import com.yzh.model.entity.MarketingActivity;
import com.yzh.model.entity.MarketingPrize;
import com.yzh.model.entity.UserReward;
import com.yzh.model.vo.*;
import com.yzh.service.LotteryPreheatService;
import com.yzh.service.MarketingActivityService;
import com.yzh.util.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/merchant/activity")
@RequiredArgsConstructor
public class MerchantActivityController {

    private static final String RATE_MAP_KEY_PREFIX = "lottery_map:";
    private static final String STOCK_KEY_PREFIX = "lottery_stock:";
    private static final String DEFAULT_STYLE = "dark_neon";

    private final MarketingActivityService marketingActivityService;
    private final MarketingActivityMapper marketingActivityMapper;
    private final MarketingPrizeMapper marketingPrizeMapper;
    private final UserRewardMapper userRewardMapper;
    private final LotteryPreheatService lotteryPreheatService;
    private final StringRedisTemplate stringRedisTemplate;

    @PostMapping("/create")
    public ChatResponse<CreateMarketingActivityResponse> create(@RequestBody CreateMarketingActivityRequest request) {
        try {
            CreateMarketingActivityResponse response = marketingActivityService.createActivity(currentMerchantId(), request);
            return ChatResponse.success(response);
        } catch (Exception e) {
            log.error("[MerchantActivityController] create failed: {}", e.getMessage(), e);
            return ChatResponse.error("创建活动失败: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    public ChatResponse<List<MarketingActivity>> list() {
        try {
            String merchantId = currentMerchantId();
            List<MarketingActivity> list = marketingActivityMapper.selectList(
                    new LambdaQueryWrapper<MarketingActivity>()
                            .eq(MarketingActivity::getMerchantId, merchantId)
                            .orderByDesc(MarketingActivity::getCreateTime)
            );
            return ChatResponse.success(list);
        } catch (Exception e) {
            log.error("[MerchantActivityController] list failed: {}", e.getMessage(), e);
            return ChatResponse.error("查询活动失败: " + e.getMessage());
        }
    }

    @GetMapping("/overview-list")
    public ChatResponse<List<MerchantActivityOverviewVO>> overviewList() {
        try {
            String merchantId = currentMerchantId();
            List<MarketingActivity> activities = marketingActivityMapper.selectList(
                    new LambdaQueryWrapper<MarketingActivity>()
                            .eq(MarketingActivity::getMerchantId, merchantId)
                            .orderByDesc(MarketingActivity::getCreateTime)
            );

            if (activities == null || activities.isEmpty()) {
                return ChatResponse.success(List.of());
            }

            List<Long> activityIds = activities.stream().map(MarketingActivity::getId).collect(Collectors.toList());
            List<MarketingPrize> allPrizes = marketingPrizeMapper.selectList(
                    new LambdaQueryWrapper<MarketingPrize>().in(MarketingPrize::getActivityId, activityIds)
            );

            Map<Long, Integer> prizeCountMap = new HashMap<>();
            Map<Long, Long> totalStockMap = new HashMap<>();
            Map<Long, Long> surplusStockMap = new HashMap<>();
            for (MarketingPrize prize : allPrizes) {
                Long aid = prize.getActivityId();
                prizeCountMap.put(aid, prizeCountMap.getOrDefault(aid, 0) + 1);
                totalStockMap.put(aid, totalStockMap.getOrDefault(aid, 0L) + safeInt(prize.getTotalStock()));
                surplusStockMap.put(aid, surplusStockMap.getOrDefault(aid, 0L) + safeInt(prize.getSurplusStock()));
            }

            List<MerchantActivityOverviewVO> list = new ArrayList<>();
            for (MarketingActivity activity : activities) {
                Long activityId = activity.getId();
                Long rewardCount = userRewardMapper.selectCount(
                        new LambdaQueryWrapper<UserReward>().eq(UserReward::getActivityId, activityId)
                );
                Long pendingCount = userRewardMapper.selectCount(
                        new LambdaQueryWrapper<UserReward>()
                                .eq(UserReward::getActivityId, activityId)
                                .eq(UserReward::getAwardState, 0)
                );

                String style = (activity.getPageStyle() == null || activity.getPageStyle().isBlank())
                        ? DEFAULT_STYLE : activity.getPageStyle();
                list.add(MerchantActivityOverviewVO.builder()
                        .activityId(activityId)
                        .activityName(activity.getActivityName())
                        .status(activity.getStatus())
                        .pageStyle(style)
                        .deductPoints(activity.getDeductPoints())
                        .createTime(activity.getCreateTime())
                        .prizeCount(prizeCountMap.getOrDefault(activityId, 0))
                        .totalStock(totalStockMap.getOrDefault(activityId, 0L))
                        .surplusStock(surplusStockMap.getOrDefault(activityId, 0L))
                        .rewardCount(rewardCount == null ? 0L : rewardCount)
                        .pendingRewardCount(pendingCount == null ? 0L : pendingCount)
                        .playLink("/lottery.html?activityId=" + activityId + "&style=" + style)
                        .manageLink("/reward-center.html?activityId=" + activityId + "&view=rewards")
                        .build());
            }
            return ChatResponse.success(list);
        } catch (Exception e) {
            log.error("[MerchantActivityController] overview list failed: {}", e.getMessage(), e);
            return ChatResponse.error("查询活动总览失败: " + e.getMessage());
        }
    }

    @PostMapping("/status")
    public ChatResponse<Map<String, Object>> updateStatus(@RequestBody UpdateActivityStatusRequest request) {
        try {
            if (request.getActivityId() == null || request.getActivityId() <= 0) {
                return ChatResponse.error("activityId 非法");
            }

            String action = request.getAction() == null ? "" : request.getAction().trim().toLowerCase(Locale.ROOT);
            if (!"publish".equals(action) && !"stop".equals(action)) {
                return ChatResponse.error("action 仅支持 publish / stop");
            }

            String merchantId = currentMerchantId();
            MarketingActivity activity = assertOwnedActivity(request.getActivityId(), merchantId);

            if ("publish".equals(action)) {
                lotteryPreheatService.preheat(activity.getId());
                activity.setStatus(1);
            } else {
                lotteryPreheatService.clearPreheatCache(activity.getId());
                activity.setStatus(0);
            }
            activity.setUpdateTime(LocalDateTime.now());
            marketingActivityMapper.updateById(activity);

            Map<String, Object> payload = new HashMap<>();
            payload.put("activityId", activity.getId());
            payload.put("activityName", activity.getActivityName());
            payload.put("status", activity.getStatus());
            payload.put("message", "publish".equals(action) ? "活动已发布并预热" : "活动已停止并清理缓存");
            return ChatResponse.success(payload);
        } catch (Exception e) {
            log.error("[MerchantActivityController] update status failed: {}", e.getMessage(), e);
            return ChatResponse.error("设置活动状态失败: " + e.getMessage());
        }
    }

    @PostMapping("/reconcile-stock")
    public ChatResponse<StockReconcileResultVO> reconcileStock(@RequestBody StockReconcileRequest request) {
        try {
            if (request.getActivityId() == null || request.getActivityId() <= 0) {
                return ChatResponse.error("activityId 非法");
            }
            String mode = request.getMode() == null ? "CHECK" : request.getMode().trim().toUpperCase(Locale.ROOT);
            if (!"CHECK".equals(mode) && !"REPAIR".equals(mode)) {
                return ChatResponse.error("mode 仅支持 CHECK / REPAIR");
            }

            String merchantId = currentMerchantId();
            assertOwnedActivity(request.getActivityId(), merchantId);

            boolean repaired = false;
            if ("REPAIR".equals(mode)) {
                lotteryPreheatService.preheat(request.getActivityId());
                repaired = true;
            }

            StockReconcileResultVO result = doReconcileCheck(request.getActivityId(), mode, repaired);
            return ChatResponse.success(result);
        } catch (Exception e) {
            log.error("[MerchantActivityController] reconcile stock failed: {}", e.getMessage(), e);
            return ChatResponse.error("库存对账失败: " + e.getMessage());
        }
    }

    private StockReconcileResultVO doReconcileCheck(Long activityId, String mode, boolean repaired) {
        List<MarketingPrize> prizes = marketingPrizeMapper.selectList(
                new LambdaQueryWrapper<MarketingPrize>().eq(MarketingPrize::getActivityId, activityId)
        );

        List<StockReconcileItemVO> mismatches = new ArrayList<>();
        Set<String> validStockKeys = new HashSet<>();

        for (MarketingPrize prize : prizes) {
            Integer mysqlStock = normalizeStock(prize.getSurplusStock(), prize.getTotalStock());
            String stockKey = STOCK_KEY_PREFIX + activityId + ":" + prize.getId();
            validStockKeys.add(stockKey);

            String redisRaw = stringRedisTemplate.opsForValue().get(stockKey);
            if (redisRaw == null) {
                mismatches.add(StockReconcileItemVO.builder()
                        .prizeId(prize.getId())
                        .prizeName(prize.getPrizeName())
                        .mysqlStock(mysqlStock)
                        .redisStock(null)
                        .diff(mysqlStock)
                        .issueType("MISSING_REDIS")
                        .redisKey(stockKey)
                        .build());
                continue;
            }

            Integer redisStock;
            try {
                redisStock = Integer.parseInt(redisRaw);
            } catch (Exception e) {
                mismatches.add(StockReconcileItemVO.builder()
                        .prizeId(prize.getId())
                        .prizeName(prize.getPrizeName())
                        .mysqlStock(mysqlStock)
                        .redisStock(null)
                        .diff(mysqlStock)
                        .issueType("INVALID_REDIS")
                        .redisKey(stockKey)
                        .build());
                continue;
            }

            if (!Objects.equals(redisStock, mysqlStock)) {
                mismatches.add(StockReconcileItemVO.builder()
                        .prizeId(prize.getId())
                        .prizeName(prize.getPrizeName())
                        .mysqlStock(mysqlStock)
                        .redisStock(redisStock)
                        .diff(mysqlStock - redisStock)
                        .issueType("VALUE_MISMATCH")
                        .redisKey(stockKey)
                        .build());
            }
        }

        Set<String> redisKeys = stringRedisTemplate.keys(STOCK_KEY_PREFIX + activityId + ":*");
        if (redisKeys != null) {
            for (String redisKey : redisKeys) {
                if (!validStockKeys.contains(redisKey)) {
                    Integer redisStock = null;
                    String raw = stringRedisTemplate.opsForValue().get(redisKey);
                    if (raw != null) {
                        try {
                            redisStock = Integer.parseInt(raw);
                        } catch (Exception ignore) {
                            // ignore
                        }
                    }
                    mismatches.add(StockReconcileItemVO.builder()
                            .prizeId(null)
                            .prizeName("未知奖品")
                            .mysqlStock(null)
                            .redisStock(redisStock)
                            .diff(null)
                            .issueType("ORPHAN_REDIS_KEY")
                            .redisKey(redisKey)
                            .build());
                }
            }
        }

        boolean rateMapExists = Boolean.TRUE.equals(stringRedisTemplate.hasKey(RATE_MAP_KEY_PREFIX + activityId));
        String msg;
        if (mismatches.isEmpty() && rateMapExists) {
            msg = repaired ? "修复完成，Redis 与 MySQL 库存一致" : "检查通过，库存一致";
        } else if (repaired) {
            msg = "已执行修复，但仍存在差异，请检查日志";
        } else {
            msg = "检查完成，发现库存差异";
        }

        return StockReconcileResultVO.builder()
                .activityId(activityId)
                .mode(mode)
                .repaired(repaired)
                .rateMapExists(rateMapExists)
                .checkedPrizeCount(prizes.size())
                .mismatchCount(mismatches.size())
                .mismatches(mismatches)
                .message(msg)
                .build();
    }

    private int normalizeStock(Integer surplusStock, Integer totalStock) {
        Integer stock = (surplusStock != null) ? surplusStock : totalStock;
        if (stock == null || stock < 0) return 0;
        return stock;
    }

    private long safeInt(Integer value) {
        return value == null ? 0L : Math.max(value, 0);
    }

    private MarketingActivity assertOwnedActivity(Long activityId, String merchantId) {
        MarketingActivity activity = marketingActivityMapper.selectById(activityId);
        if (activity == null) {
            throw new IllegalArgumentException("活动不存在");
        }
        if (!merchantId.equals(activity.getMerchantId())) {
            throw new IllegalArgumentException("无权限操作该活动");
        }
        return activity;
    }

    private String currentMerchantId() {
        String merchantId = UserContext.getUsername();
        if (merchantId == null || merchantId.isBlank()) {
            merchantId = UserContext.getUserId();
        }
        if (merchantId == null || merchantId.isBlank()) {
            throw new IllegalArgumentException("商户未登录");
        }
        return merchantId;
    }
}

