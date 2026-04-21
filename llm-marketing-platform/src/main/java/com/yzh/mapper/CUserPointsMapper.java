package com.yzh.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzh.model.entity.CUserPoints;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface CUserPointsMapper extends BaseMapper<CUserPoints> {

    @Update("UPDATE c_user_points " +
            "SET used_points = used_points + #{cost}, remain_points = remain_points - #{cost}, update_time = NOW() " +
            "WHERE activity_id = #{activityId} AND c_user_id = #{cUserId} AND remain_points >= #{cost}")
    int deductForDraw(@Param("activityId") Long activityId,
                      @Param("cUserId") String cUserId,
                      @Param("cost") BigDecimal cost);

    @Update("UPDATE c_user_points " +
            "SET used_points = used_points - #{points}, remain_points = remain_points + #{points}, update_time = NOW() " +
            "WHERE activity_id = #{activityId} AND c_user_id = #{cUserId} AND used_points >= #{points}")
    int refund(@Param("activityId") Long activityId,
               @Param("cUserId") String cUserId,
               @Param("points") BigDecimal points);

    @Update("UPDATE c_user_points " +
            "SET total_points = total_points + #{points}, remain_points = remain_points + #{points}, update_time = NOW() " +
            "WHERE activity_id = #{activityId} AND c_user_id = #{cUserId}")
    int grantToOne(@Param("activityId") Long activityId,
                   @Param("cUserId") String cUserId,
                   @Param("points") BigDecimal points);

    @Update("UPDATE c_user_points " +
            "SET total_points = total_points + #{points}, remain_points = remain_points + #{points}, update_time = NOW() " +
            "WHERE activity_id = #{activityId}")
    int grantToAll(@Param("activityId") Long activityId,
                   @Param("points") BigDecimal points);
}

