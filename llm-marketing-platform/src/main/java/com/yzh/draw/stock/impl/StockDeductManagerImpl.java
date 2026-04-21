package com.yzh.draw.stock.impl;

import com.yzh.draw.stock.IStockDeductManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class StockDeductManagerImpl implements IStockDeductManager {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean deductStock(Long activityId, String prizeId, String requestId) {
        // 1. SETNX 加锁防重 (记录该次流水ID，防止网关层产生的重试发过来的恶意重复请求)
        String lockKey = "lottery_req_lock:" + activityId + ":" + requestId;
        Boolean getLock = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.MINUTES);
        
        if (Boolean.FALSE.equals(getLock)) {
            log.warn("防重锁拦截: 该流水号已存在 requestId={}", requestId);
            return false;
        }

        // 2. Lua 脚本原子扣减库存，解决并发抢夺下超买的问题
        String stockKey = "lottery_stock:" + activityId + ":" + prizeId;
        String luaScript = 
                "local stock = tonumber(redis.call('get', KEYS[1])) " +
                "if stock and stock > 0 then " +
                "    redis.call('decr', KEYS[1]) " +
                "    return 1 " +
                "end " +
                "return 0 ";
        
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(luaScript);
        redisScript.setResultType(Long.class);

        Long result = stringRedisTemplate.execute(redisScript, Collections.singletonList(stockKey));
        
        if (result == null || result == 0) {
            log.warn("奖品库存扣减失败(库存不足): activityId={}, prizeId={}", activityId, prizeId);
            // 锁的释放取决于业务，由于未抢到，这里也可选择把刚才的 lock 删掉以便此单号能重新玩，或保留以阻断重玩 (此处选择保留锁阻断)。
            return false;
        }
        
        return true;
    }

    @Override
    public boolean rollbackStock(Long activityId, String prizeId, String requestId) {
        String stockKey = "lottery_stock:" + activityId + ":" + prizeId;
        stringRedisTemplate.opsForValue().increment(stockKey);
         
        String lockKey = "lottery_req_lock:" + activityId + ":" + requestId;
        stringRedisTemplate.delete(lockKey);
        return true;
    }
}
