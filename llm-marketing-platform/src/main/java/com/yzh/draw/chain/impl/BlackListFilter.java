package com.yzh.draw.chain.impl;

import com.yzh.draw.chain.IDrawFilter;
import com.yzh.draw.model.DrawContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 黑名单拦截器：判定用户是否在风控异常名单中
 */
@Component
@Order(10) // 数值越小优先级越高，最先被执行
public class BlackListFilter implements IDrawFilter {
    
    @Override
    public boolean doFilter(DrawContext context) {
        String userId = context.getRequest().getUserId();
        
        // 模拟黑名单查询 (后续可扩展为读 Redis 集合或布隆过滤器)
        if ("blacklist_user".equals(userId)) {
            context.setIntercept(true);
            context.setInterceptMessage("您的账号存在风险或违规行为，拦截抽奖。");
            return false;
        }
        
        return true;
    }
}
