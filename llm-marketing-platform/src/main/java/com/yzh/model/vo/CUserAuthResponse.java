package com.yzh.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CUserAuthResponse {

    @JsonProperty("cUserId")
    private String cUserId;

    @JsonProperty("username")
    private String username;

    @JsonProperty("nickname")
    private String nickname;

    @JsonProperty("mobile")
    private String mobile;

    @JsonProperty("remainPoints")
    private java.math.BigDecimal remainPoints;

    @JsonProperty("newUser")
    private Boolean newUser;

    @JsonProperty("loginMessage")
    private String loginMessage;
}
