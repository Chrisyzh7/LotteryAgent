package com.yzh.service;

import com.yzh.model.vo.CreateMarketingActivityRequest;
import com.yzh.model.vo.CreateMarketingActivityResponse;

public interface MarketingActivityService {

    /**
     * B端商户创建活动（草稿态）
     */
    CreateMarketingActivityResponse createActivity(String merchantId, CreateMarketingActivityRequest request);
}

