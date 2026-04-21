package com.yzh.service;

import com.yzh.model.entity.CUser;
import com.yzh.model.vo.CUserAuthRequest;
import com.yzh.model.vo.CUserAuthResponse;
import com.yzh.model.vo.CUserPointsVO;
import com.yzh.model.vo.CUserRewardVO;

import java.util.List;

public interface CUserService {

    CUserAuthResponse auth(CUserAuthRequest request);

    void ensureExists(String cUserId);

    CUser resolveByUserRef(String userRef);

    CUserPointsVO getPoints(Long activityId, String cUserId);

    List<CUserRewardVO> listRewards(String cUserId);
}
