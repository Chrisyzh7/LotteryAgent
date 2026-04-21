package com.yzh.draw.chain;

import com.yzh.draw.model.DrawContext;

/**
 * 抽奖前置拦截过滤器接口
 */
public interface IDrawFilter {

    /**
     * 执行过滤逻辑
     * @param context 抽奖上下文
     * @return true: 校验通过，放行到下一节点； false: 拦截，终止流程并直接返回
     */
    boolean doFilter(DrawContext context);
}
