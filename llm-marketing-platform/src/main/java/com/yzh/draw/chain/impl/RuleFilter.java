package com.yzh.draw.chain.impl;

import com.yzh.draw.chain.IDrawFilter;
import com.yzh.draw.model.DrawContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 活动规则拦截器：校验活动是否有效、在进行中
 */
@Component
@Order(20)
public class RuleFilter implements IDrawFilter {
    
    @Override
    public boolean doFilter(DrawContext context) {
        Long activityId = context.getRequest().getActivityId();
        
        // 此处可拉取活动缓存，如果为null说明活动已下线
        if (activityId == null || activityId <= 0) {
            context.setIntercept(true);
            context.setInterceptMessage("该活动不存在或已过期。");
            return false;
        }
        return true;
    }
}
