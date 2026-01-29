package com.varista.service;

import com.varista.model.LoginRequest;
import com.varista.model.LoginResponse;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    public LoginResponse login(LoginRequest loginRequest) {
        LoginResponse loginResponse = new LoginResponse();
        if (loginRequest.getUsername().equals("admin@gmail.com") && loginRequest.getPassword().equals("password")) {
            loginResponse.setStatus("SUCCESS");
            loginResponse.setMessage("Login successful");
            loginResponse.setRole("ADMIN");
            loginResponse.setUserId("1");
            loginResponse.setCompanyId("1");
            loginResponse.setCompanyName("Varista Technologies");
            loginResponse.setLogin_ts(System.currentTimeMillis());
            loginResponse.setToken("dummy-jwt-token");
            loginResponse.setRefreshToken("dummy-refresh-token");
            loginResponse.setTokenExpiryTs(System.currentTimeMillis() + 3600000); // 1 hour expiry
            loginResponse.setRefreshTokenExpiryTs(System.currentTimeMillis() + 86400000); // 24 hours expiry
            loginResponse.setUserName("Bhanu");


            return loginResponse;
        }

        return null;
    }

    public LoginResponse register(LoginRequest loginRequest) {
        return null;
    }
}
