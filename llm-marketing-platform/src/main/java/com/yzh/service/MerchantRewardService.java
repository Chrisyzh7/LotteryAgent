package com.yzh.service;

import com.yzh.model.vo.MerchantRewardRecordVO;

import java.util.List;

public interface MerchantRewardService {

    List<MerchantRewardRecordVO> listRecords(Long activityId, String merchantId);

    void updateState(Long rewardId, Integer awardState, String merchantId);
}

