package com.yzh.service;

import com.yzh.model.vo.CUserPointsVO;

import java.math.BigDecimal;

public interface CUserPointsService {

    CUserPointsVO ensureAndGet(Long activityId, String cUserId);

    BigDecimal deductForDraw(Long activityId, String cUserId, BigDecimal cost);

    BigDecimal refund(Long activityId, String cUserId, BigDecimal points);

    BigDecimal grantToOne(Long activityId, String cUserId, BigDecimal points, String merchantId);

    int grantToAll(Long activityId, BigDecimal points, boolean includeFutureUsers, String merchantId);
}

