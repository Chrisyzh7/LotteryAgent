package com.yzh.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yzh.mapper.MarketingActivityMapper;
import com.yzh.model.entity.MarketingActivity;
import com.yzh.service.LotteryPreheatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Optional startup preheat switch.
 * Default disabled: avoid warming all merchants' activities on every restart.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OnlineActivityAutoPreheatInitializer {

    private final MarketingActivityMapper marketingActivityMapper;
    private final LotteryPreheatService lotteryPreheatService;

    @Value("${lottery.auto-preheat-on-startup:false}")
    private boolean autoPreheatOnStartup;

    @EventListener(ApplicationReadyEvent.class)
    public void autoPreheatOnlineActivities() {
        if (!autoPreheatOnStartup) {
            log.info("[AutoPreheat] startup auto preheat is disabled.");
            return;
        }

        try {
            List<MarketingActivity> onlineActivities = marketingActivityMapper.selectList(
                    new LambdaQueryWrapper<MarketingActivity>()
                            .eq(MarketingActivity::getStatus, 1)
                            .orderByAsc(MarketingActivity::getId)
                            .last("LIMIT 50")
            );

            if (onlineActivities == null || onlineActivities.isEmpty()) {
                log.info("[AutoPreheat] no online activities found, skip.");
                return;
            }

            int success = 0;
            for (MarketingActivity activity : onlineActivities) {
                try {
                    lotteryPreheatService.preheat(activity.getId());
                    success++;
                } catch (Exception e) {
                    log.warn("[AutoPreheat] failed activityId={}, err={}", activity.getId(), e.getMessage());
                }
            }
            log.info("[AutoPreheat] finished total={}, success={}", onlineActivities.size(), success);
        } catch (Exception e) {
            log.error("[AutoPreheat] unexpected error: {}", e.getMessage(), e);
        }
    }
}

