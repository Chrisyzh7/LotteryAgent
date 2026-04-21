package com.yzh.controller;

import com.yzh.model.entity.CUser;
import com.yzh.model.vo.ChatResponse;
import com.yzh.model.vo.MerchantGrantPointsRequest;
import com.yzh.model.vo.MerchantRewardRecordVO;
import com.yzh.model.vo.UpdateRewardStateRequest;
import com.yzh.service.CUserPointsService;
import com.yzh.service.CUserService;
import com.yzh.service.MerchantRewardService;
import com.yzh.util.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/merchant/reward")
@RequiredArgsConstructor
public class MerchantRewardController {

    private final MerchantRewardService merchantRewardService;
    private final CUserPointsService cUserPointsService;
    private final CUserService cUserService;

    @GetMapping("/records")
    public ChatResponse<List<MerchantRewardRecordVO>> records(@RequestParam("activityId") Long activityId) {
        try {
            return ChatResponse.success(merchantRewardService.listRecords(activityId, currentMerchantId()));
        } catch (Exception e) {
            log.error("[MerchantRewardController] 查询中奖记录失败: {}", e.getMessage(), e);
            return ChatResponse.error("查询中奖记录失败: " + e.getMessage());
        }
    }

    @PostMapping("/state")
    public ChatResponse<String> updateState(@RequestBody UpdateRewardStateRequest request) {
        try {
            merchantRewardService.updateState(request.getRewardId(), request.getAwardState(), currentMerchantId());
            return ChatResponse.success("状态更新成功");
        } catch (Exception e) {
            log.error("[MerchantRewardController] 更新发奖状态失败: {}", e.getMessage(), e);
            return ChatResponse.error("更新发奖状态失败: " + e.getMessage());
        }
    }

    @PostMapping("/grant-points")
    public ChatResponse<Map<String, Object>> grantPoints(@RequestBody MerchantGrantPointsRequest request) {
        try {
            String merchantId = currentMerchantId();
            if (request.getActivityId() == null || request.getActivityId() <= 0) {
                return ChatResponse.error("activityId 非法");
            }
            if (request.getPoints() == null || request.getPoints().compareTo(BigDecimal.ZERO) <= 0) {
                return ChatResponse.error("points 必须大于 0");
            }

            boolean applyToAll = Boolean.TRUE.equals(request.getApplyToAll());
            Map<String, Object> payload = new HashMap<>();

            if (applyToAll) {
                boolean includeFutureUsers = request.getIncludeFutureUsers() == null || request.getIncludeFutureUsers();
                int affected = cUserPointsService.grantToAll(
                        request.getActivityId(),
                        request.getPoints(),
                        includeFutureUsers,
                        merchantId
                );
                payload.put("mode", "all");
                payload.put("affected", affected);
                payload.put("includeFutureUsers", includeFutureUsers);
                payload.put("message", "全体用户加积分成功");
            } else {
                String userRef = firstNotBlank(request.getUserRef(), request.getCUserId());
                if (userRef == null) {
                    return ChatResponse.error("指定用户加积分时，用户标识不能为空");
                }
                CUser target = cUserService.resolveByUserRef(userRef);

                BigDecimal remain = cUserPointsService.grantToOne(
                        request.getActivityId(),
                        target.getCUserId(),
                        request.getPoints(),
                        merchantId
                );
                payload.put("mode", "single");
                payload.put("cUserId", target.getCUserId());
                payload.put("username", target.getUsername());
                payload.put("nickname", target.getNickname());
                payload.put("mobile", target.getMobile());
                payload.put("remainPoints", remain);
                payload.put("message", "指定用户加积分成功");
            }

            return ChatResponse.success(payload);
        } catch (Exception e) {
            log.error("[MerchantRewardController] 执行加积分失败: {}", e.getMessage(), e);
            return ChatResponse.error("执行加积分失败: " + e.getMessage());
        }
    }

    private String firstNotBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        if (second != null && !second.trim().isEmpty()) {
            return second.trim();
        }
        return null;
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
