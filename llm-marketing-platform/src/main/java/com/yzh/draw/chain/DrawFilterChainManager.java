package com.yzh.draw.chain;

import com.yzh.draw.model.DrawContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 过滤器责任链管理类
 */
@Component
public class DrawFilterChainManager {

    private final List<IDrawFilter> filters;

    /**
     * 利用 Spring 机制，配合 @Order 自动注入所有实现类并排序
     */
    public DrawFilterChainManager(List<IDrawFilter> filters) {
        this.filters = filters;
    }

    /**
     * 遍历执行所有拦截规则
     * @return true表示全部通过, false表示被其中一个拦截
     */
    public boolean doCheck(DrawContext context) {
        if (filters == null || filters.isEmpty()) {
            return true; // 没有过滤器默认放行
        }
        for (IDrawFilter filter : filters) {
            if (!filter.doFilter(context)) {
                return false;
            }
        }
        return true;
    }
}
