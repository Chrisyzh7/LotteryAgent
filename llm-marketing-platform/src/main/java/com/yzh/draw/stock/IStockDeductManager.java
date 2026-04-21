package com.yzh.draw.stock;

public interface IStockDeductManager {

    /**
     * 发奖前置扣减库存，基于 Lua 脚本和 SETNX 防重
     *
     * @param activityId 活动ID
     * @param prizeId    奖品ID
     * @param requestId  请求流水号(幂等性防重)
     * @return 成功true，失败false
     */
    boolean deductStock(Long activityId, String prizeId, String requestId);

    /**
     * 恢复/补偿库存 (用于后续落库发奖失败或用户取消领取的场景回滚)
     * 
     * @param activityId 活动ID
     * @param prizeId    奖品ID
     * @param requestId  请求流水号
     * @return 成功true，失败false
     */
    boolean rollbackStock(Long activityId, String prizeId, String requestId);
}
