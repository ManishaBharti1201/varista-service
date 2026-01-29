package com.varista.model;

import lombok.Data;

@Data
public class LoginResponse {

    private String token;
    private String tokenType = "Bearer";
    private Long tokenExpiryTs;
    private String role;
    private String userId;
    private String companyId;
    private String companyName;
    private Long login_ts;
    private String refreshToken;
    private Long refreshTokenExpiryTs;
    private String userName;
    private String status;
    private String message;
}
