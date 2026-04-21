package com.yzh.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yzh.model.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户账户 Mapper 接口
 */
@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {
    // 基础 CRUD 由 MyBatis-Plus 提供
}
