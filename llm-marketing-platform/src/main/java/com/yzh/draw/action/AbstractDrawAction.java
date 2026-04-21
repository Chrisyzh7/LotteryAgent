package com.yzh.draw.action;

import com.yzh.draw.model.DrawContext;
import com.yzh.draw.model.request.DrawRequest;
import com.yzh.draw.model.response.DrawResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 抽奖核心模板方法骨架
 */
@Slf4j
public abstract class AbstractDrawAction {

    /**
     * 执行抽奖核心流程
     */
    public DrawResponse doDrawProcess(DrawRequest request) {
        log.info("开始执行抽奖流程, request={}", request);
        DrawContext context = new DrawContext();
        context.setRequest(request);
        context.setResponse(new DrawResponse(false, null, null, "系统异常", null));

        try {
            // 1. 执行前置校验
            if (!doPreCheck(context)) {
                log.info("抽奖流程被前置校验拦截: {}", context.getInterceptMessage());
                context.getResponse().setSuccess(false);
                context.getResponse().setMessage(context.getInterceptMessage());
                return context.getResponse();
            }

            // 2. 核心抽奖
            if (!doDraw(context)) {
                log.info("抽奖核心计算未中奖或被兜底");
                if (context.getResponse().getMessage() == null || context.getResponse().getMessage().isBlank()) {
                    context.getResponse().setMessage(context.getInterceptMessage());
                }
                return context.getResponse();
            }

            // 3. 库存扣减
            if (!doDeduct(context)) {
                log.info("奖品库存扣减失败或防重拦截，转入兜底发奖");
                return doPostReward(context, false);
            }

            // 4. 发奖记录
            return doPostReward(context, true);

        } catch (Exception e) {
            log.error("抽奖执行异常 request={}", request, e);
            context.getResponse().setSuccess(false);
            context.getResponse().setMessage("系统内部异常");
        }

        return context.getResponse();
    }

    /** 步骤1：前置检查（如责任链） */
    protected abstract boolean doPreCheck(DrawContext context);

    /** 步骤2：执行算法抽奖 */
    protected abstract boolean doDraw(DrawContext context);

    /** 步骤3：扣减库存 */
    protected abstract boolean doDeduct(DrawContext context);

    /** 步骤4：后置发奖 */
    protected abstract DrawResponse doPostReward(DrawContext context, boolean deductSuccess);
}