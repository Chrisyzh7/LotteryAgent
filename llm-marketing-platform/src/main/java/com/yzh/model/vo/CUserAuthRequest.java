package com.yzh.model.vo;

import lombok.Data;

@Data
public class CUserAuthRequest {

    private Long activityId;

    private String username;

    private String password;

    private String nickname;

    private String mobile;

    /**
     * Optional legacy identity for account binding/migration.
     */
    private String cUserId;
}
