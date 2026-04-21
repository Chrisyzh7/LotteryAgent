package com.yzh.controller;

import com.yzh.model.vo.CUserAuthRequest;
import com.yzh.model.vo.CUserAuthResponse;
import com.yzh.model.vo.CUserPointsVO;
import com.yzh.model.vo.CUserRewardVO;
import com.yzh.model.vo.ChatResponse;
import com.yzh.service.CUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/c-user")
@RequiredArgsConstructor
public class CUserController {

    private final CUserService cUserService;

    @PostMapping("/auth")
    public ChatResponse<Map<String, Object>> auth(@RequestBody CUserAuthRequest request) {
        try {
            CUserAuthResponse auth = cUserService.auth(request);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("cUserId", auth.getCUserId());
            payload.put("username", auth.getUsername());
            payload.put("nickname", auth.getNickname());
            payload.put("mobile", auth.getMobile());
            payload.put("remainPoints", auth.getRemainPoints());
            payload.put("newUser", auth.getNewUser());
            payload.put("loginMessage", auth.getLoginMessage());
            return ChatResponse.success(payload);
        } catch (Exception e) {
            log.error("[CUserController] C端登录失败: {}", e.getMessage(), e);
            return ChatResponse.error("C端登录失败: " + e.getMessage());
        }
    }

    @GetMapping("/rewards")
    public ChatResponse<List<CUserRewardVO>> rewards(@RequestParam("cUserId") String cUserId) {
        try {
            return ChatResponse.success(cUserService.listRewards(cUserId));
        } catch (Exception e) {
            log.error("[CUserController] 查询中奖记录失败: {}", e.getMessage(), e);
            return ChatResponse.error("查询中奖记录失败: " + e.getMessage());
        }
    }

    @GetMapping("/points")
    public ChatResponse<CUserPointsVO> points(@RequestParam("activityId") Long activityId,
                                              @RequestParam("cUserId") String cUserId) {
        try {
            return ChatResponse.success(cUserService.getPoints(activityId, cUserId));
        } catch (Exception e) {
            log.error("[CUserController] 查询积分失败: {}", e.getMessage(), e);
            return ChatResponse.error("查询积分失败: " + e.getMessage());
        }
    }
}
