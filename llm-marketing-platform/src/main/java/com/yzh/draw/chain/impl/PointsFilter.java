package com.yzh.draw.chain.impl;

import com.yzh.draw.chain.IDrawFilter;
import com.yzh.draw.model.DrawContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 积分/次数拦截器：确保用户有足够的调用资本
 */
@Component
@Order(30)
public class PointsFilter implements IDrawFilter {
    
    @Override
    public boolean doFilter(DrawContext context) {
        // TODO: 查询用户 UserAccount 余额，判定扣减是否足够
        // 假设通过
        return true;
    }
}
