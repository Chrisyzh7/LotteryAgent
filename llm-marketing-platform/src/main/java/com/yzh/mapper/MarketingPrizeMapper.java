package com.yzh.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzh.model.entity.MarketingPrize;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MarketingPrizeMapper extends BaseMapper<MarketingPrize> {

    @Update("UPDATE marketing_prizes " +
            "SET surplus_stock = surplus_stock - 1, update_time = NOW() " +
            "WHERE id = #{prizeId} AND activity_id = #{activityId} AND surplus_stock > 0")
    int deductSurplusStock(@Param("activityId") Long activityId, @Param("prizeId") Long prizeId);

    @Update("UPDATE marketing_prizes " +
            "SET surplus_stock = surplus_stock + 1, update_time = NOW() " +
            "WHERE id = #{prizeId} AND activity_id = #{activityId}")
    int rollbackSurplusStock(@Param("activityId") Long activityId, @Param("prizeId") Long prizeId);
}
