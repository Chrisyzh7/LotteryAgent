package com.yzh.draw.action.impl;

import com.yzh.draw.action.AbstractDrawAction;
import com.yzh.draw.chain.DrawFilterChainManager;
import com.yzh.draw.model.DrawContext;
import com.yzh.draw.model.response.DrawResponse;
import com.yzh.draw.stock.IStockDeductManager;
import com.yzh.draw.strategy.IDrawAlgorithm;
import com.yzh.mapper.MarketingPrizeMapper;
import com.yzh.mapper.UserRewardMapper;
import com.yzh.model.entity.MarketingPrize;
import com.yzh.model.entity.UserReward;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
public class DefaultDrawActionImpl extends AbstractDrawAction {

    @Resource
    private DrawFilterChainManager chainManager;

    @Resource
    private Map<String, IDrawAlgorithm> algorithmMap;

    @Resource
    private IStockDeductManager stockDeductManager;

    @Resource
    private UserRewardMapper userRewardMapper;

    @Resource
    private MarketingPrizeMapper marketingPrizeMapper;

    @Override
    protected boolean doPreCheck(DrawContext context) {
        return chainManager.doCheck(context);
    }

    @Override
    protected boolean doDraw(DrawContext context) {
        Long activityId = context.getRequest().getActivityId();
        IDrawAlgorithm algorithm = algorithmMap.get("redisHashDrawAlgorithm");

        if (!algorithm.isExist(activityId)) {
            log.error("draw algorithm is not preheated, activityId={}", activityId);
            context.getResponse().setSuccess(false);
            context.setInterceptMessage("活动尚未预热数据");
            return false;
        }

        String prizeId = algorithm.randomDraw(activityId);
        if ("NO_AWARD".equals(prizeId)) {
            context.getResponse().setSuccess(true);
            context.getResponse().setPrizeId("NO_AWARD");
            context.getResponse().setPrizeName("谢谢参与");
            context.getResponse().setMessage("很遗憾，未中奖");
            return false;
        }

        context.setPrizeInfo(prizeId);
        return true;
    }

    @Override
    protected boolean doDeduct(DrawContext context) {
        Long activityId = context.getRequest().getActivityId();
        String prizeId = (String) context.getPrizeInfo();
        String requestId = context.getRequest().getRequestId();
        return stockDeductManager.deductStock(activityId, prizeId, requestId);
    }

    @Override
    protected DrawResponse doPostReward(DrawContext context, boolean deductSuccess) {
        if (!deductSuccess) {
            log.info("stock deduct failed, fallback reward");
            context.getResponse().setSuccess(true);
            context.getResponse().setPrizeId("FALLBACK_VIRTUAL");
            context.getResponse().setPrizeName("奖品已发完，补偿 50 平台积分");
            context.getResponse().setMessage("库存不足，已发放补偿");
            return context.getResponse();
        }

        Long activityId = context.getRequest().getActivityId();
        String requestId = context.getRequest().getRequestId();
        String prizeId = (String) context.getPrizeInfo();
        context.getResponse().setPrizeId(prizeId);

        Long prizeIdLong;
        try {
            prizeIdLong = Long.parseLong(prizeId);
        } catch (Exception e) {
            log.error("invalid prizeId={}, rollback redis stock", prizeId, e);
            rollbackRedis(activityId, prizeId, requestId);
            context.getResponse().setSuccess(false);
            context.getResponse().setMessage("奖品数据异常，请稍后再试");
            return context.getResponse();
        }

        // Step-1: Redis stock already deducted in doDeduct, now deduct MySQL stock as source of truth.
        int dbDeduct = marketingPrizeMapper.deductSurplusStock(activityId, prizeIdLong);
        if (dbDeduct <= 0) {
            log.error("mysql stock deduct failed, activityId={}, prizeId={}, requestId={}",
                    activityId, prizeIdLong, requestId);
            rollbackRedis(activityId, prizeId, requestId);
            context.getResponse().setSuccess(false);
            context.getResponse().setMessage("奖品库存不足，请再试一次");
            return context.getResponse();
        }

        MarketingPrize prizeEntity = null;
        try {
            prizeEntity = marketingPrizeMapper.selectById(prizeIdLong);
        } catch (Exception ignore) {
            // ignore
        }

        context.getResponse().setPrizeName(prizeEntity != null ? prizeEntity.getPrizeName() : ("奖品_" + prizeId));
        context.getResponse().setMessage("恭喜您中奖");

        UserReward reward = UserReward.builder()
                .activityId(activityId)
                .cUserId(context.getRequest().getUserId())
                .prizeId(prizeIdLong)
                .requestId(requestId)
                .awardState(0)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        try {
            userRewardMapper.insert(reward);
        } catch (Exception e) {
            log.error("insert user_reward failed, rollback both mysql and redis. activityId={}, prizeId={}, requestId={}",
                    activityId, prizeIdLong, requestId, e);
            rollbackMysql(activityId, prizeIdLong);
            rollbackRedis(activityId, prizeId, requestId);
            context.getResponse().setSuccess(false);
            context.getResponse().setMessage("发奖处理异常，请稍后重试");
            return context.getResponse();
        }

        context.getResponse().setSuccess(true);
        return context.getResponse();
    }

    private void rollbackRedis(Long activityId, String prizeId, String requestId) {
        try {
            stockDeductManager.rollbackStock(activityId, prizeId, requestId);
        } catch (Exception rollbackEx) {
            log.error("rollback redis stock failed, activityId={}, prizeId={}, requestId={}",
                    activityId, prizeId, requestId, rollbackEx);
        }
    }

    private void rollbackMysql(Long activityId, Long prizeId) {
        try {
            marketingPrizeMapper.rollbackSurplusStock(activityId, prizeId);
        } catch (Exception rollbackEx) {
            log.error("rollback mysql stock failed, activityId={}, prizeId={}", activityId, prizeId, rollbackEx);
        }
    }
}

