package com.yzh.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzh.draw.action.AbstractDrawAction;
import com.yzh.draw.model.request.DrawRequest;
import com.yzh.draw.model.response.DrawResponse;
import com.yzh.mapper.MarketingActivityMapper;
import com.yzh.mapper.MarketingPrizeMapper;
import com.yzh.model.entity.MarketingActivity;
import com.yzh.model.entity.MarketingPrize;
import com.yzh.service.CUserPointsService;
import com.yzh.service.CUserService;
import com.yzh.service.LotteryPreheatService;
import com.yzh.util.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/lottery")
public class LotteryController {

    @Resource
    private MarketingActivityMapper activityMapper;

    @Resource
    private MarketingPrizeMapper prizeMapper;

    @Resource
    private AbstractDrawAction defaultDrawActionImpl;

    @Resource
    private LotteryPreheatService lotteryPreheatService;

    @Resource
    private CUserService cUserService;

    @Resource
    private CUserPointsService cUserPointsService;

    /**
     * 客户端获取当前正在进行的活动及奖品配置
     */
    @GetMapping("/info")
    public Map<String, Object> getLotteryInfo(@RequestParam(value = "activityId", required = false) Long activityId,
                                              @RequestParam(value = "cUserId", required = false) String cUserId) {
        Map<String, Object> result = new HashMap<>();

        MarketingActivity activity;
        if (activityId != null && activityId > 0) {
            activity = activityMapper.selectOne(
                    new LambdaQueryWrapper<MarketingActivity>()
                            .eq(MarketingActivity::getId, activityId)
                            .eq(MarketingActivity::getStatus, 1)
                            .last("LIMIT 1")
            );
        } else {
            activity = activityMapper.selectOne(
                    new LambdaQueryWrapper<MarketingActivity>()
                            .eq(MarketingActivity::getStatus, 1)
                            .orderByDesc(MarketingActivity::getCreateTime)
                            .last("LIMIT 1")
            );
        }

        if (activity != null) {
            List<MarketingPrize> prizes = prizeMapper.selectList(
                    new LambdaQueryWrapper<MarketingPrize>()
                            .eq(MarketingPrize::getActivityId, activity.getId())
            );
            result.put("activity", activity);
            result.put("prizes", prizes);
            if (cUserId != null && !cUserId.isBlank()) {
                try {
                    result.put("userPoints", cUserPointsService.ensureAndGet(activity.getId(), cUserId).getRemainPoints());
                } catch (Exception e) {
                    log.warn("[LotteryController] 获取活动页积分失败: {}", e.getMessage());
                }
            }
        } else {
            result.put("activity", null);
        }
        return result;
    }

    /**
     * C端客户端触发抽奖流程
     */
    @PostMapping("/draw")
    public Map<String, Object> doDraw(@RequestBody Map<String, String> params) {
        Map<String, Object> result = new HashMap<>();
        boolean deducted = false;
        BigDecimal cost = BigDecimal.ZERO;
        Long activityId = null;
        String cUserId = null;

        try {
            cUserId = params.get("userId");
            if (cUserId == null || cUserId.isBlank()) {
                result.put("code", 400);
                result.put("msg", "请先登录C端用户再抽奖");
                return result;
            }
            cUserService.ensureExists(cUserId);

            activityId = Long.parseLong(params.get("activityId"));
            MarketingActivity activity = activityMapper.selectById(activityId);
            if (activity == null || activity.getStatus() == null || activity.getStatus() != 1) {
                result.put("code", 400);
                result.put("msg", "活动不存在或未发布");
                return result;
            }

            cost = activity.getDeductPoints() == null ? BigDecimal.ZERO : activity.getDeductPoints();
            BigDecimal remainAfterDeduct = cUserPointsService.deductForDraw(activityId, cUserId, cost);
            deducted = cost.compareTo(BigDecimal.ZERO) > 0;

            DrawRequest req = new DrawRequest();
            req.setUserId(cUserId);
            req.setActivityId(activityId);
            req.setRequestId("REQ_" + UUID.randomUUID().toString().replace("-", ""));

            DrawResponse response = defaultDrawActionImpl.doDrawProcess(req);
            response.setRemainPoints(remainAfterDeduct);

            // 如果被系统异常拦截，返还积分，避免用户无感损失
            if (!response.isSuccess() && deducted) {
                BigDecimal remainAfterRefund = cUserPointsService.refund(activityId, cUserId, cost);
                response.setRemainPoints(remainAfterRefund);
            }

            result.put("code", response.isSuccess() ? 200 : 500);
            result.put("msg", response.getMessage());
            result.put("data", response);
        } catch (IllegalArgumentException e) {
            if (deducted && activityId != null && cUserId != null && cost.compareTo(BigDecimal.ZERO) > 0) {
                try {
                    cUserPointsService.refund(activityId, cUserId, cost);
                } catch (Exception ignore) {
                    // ignore
                }
            }
            result.put("code", 400);
            result.put("msg", e.getMessage());
        } catch (Exception e) {
            if (deducted && activityId != null && cUserId != null && cost.compareTo(BigDecimal.ZERO) > 0) {
                try {
                    cUserPointsService.refund(activityId, cUserId, cost);
                } catch (Exception ignore) {
                    // ignore
                }
            }
            log.error("API抽奖异常", e);
            result.put("code", 500);
            result.put("msg", "系统开小差了，请稍后再试");
        }
        return result;
    }

    /**
     * 手动预热活动奖池和库存
     */
    @PostMapping("/preheat")
    public Map<String, Object> preheat(@RequestBody Map<String, String> params) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long activityId = Long.parseLong(params.get("activityId"));
            lotteryPreheatService.preheat(activityId);
            result.put("code", 200);
            result.put("msg", "预热成功");
        } catch (Exception e) {
            log.error("活动预热失败", e);
            result.put("code", 500);
            result.put("msg", "预热失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 发布活动（状态置为进行中）并自动触发预热
     */
    @PostMapping("/publish")
    public Map<String, Object> publish(@RequestBody Map<String, String> params) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long activityId = Long.parseLong(params.get("activityId"));
            MarketingActivity activity = activityMapper.selectById(activityId);
            if (activity == null) {
                result.put("code", 404);
                result.put("msg", "活动不存在");
                return result;
            }
            String merchantId = UserContext.getUsername();
            if (merchantId == null || merchantId.isBlank()) {
                merchantId = UserContext.getUserId();
            }
            if (merchantId == null || !merchantId.equals(activity.getMerchantId())) {
                result.put("code", 403);
                result.put("msg", "无权限发布该活动");
                return result;
            }

            lotteryPreheatService.preheat(activityId);

            activity.setStatus(1);
            activity.setUpdateTime(LocalDateTime.now());
            activityMapper.updateById(activity);

            result.put("code", 200);
            result.put("msg", "活动发布成功并已完成预热");
        } catch (Exception e) {
            log.error("活动发布失败", e);
            result.put("code", 500);
            result.put("msg", "发布失败: " + e.getMessage());
        }
        return result;
    }
}