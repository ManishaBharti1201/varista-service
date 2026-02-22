package com.varista.service;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.varista.entity.User;
import com.varista.model.LoginRequest;
import com.varista.model.LoginResponse;
import com.varista.model.UserRequest;
import com.varista.repository.UserRepository;
import com.varista.utils.EmailUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailUtils emailService;

    @Value("${security.jwt.secret:change-it-in-production-at-least-32-chars}")
    private String jwtSecret;

    private static final long ACCESS_TOKEN_EXPIRY = 3600_000; // 1 hour
    private static final long REFRESH_TOKEN_EXPIRY = 86400_000; // 24 hours

    public LoginResponse login(LoginRequest loginRequest) throws Exception {
        log.info("Processing login request for user: {}", loginRequest.getUsername());

        User user = userRepository.findByEmail(loginRequest.getUsername())
                .orElseThrow(() -> new Exception("Invalid username or password"));

        // Security Note: In production, use BCryptPasswordEncoder.matches(raw, encoded)
        if (!loginRequest.getPassword().equals(user.getPassword())) {
            log.warn("Password mismatch for user: {}", loginRequest.getUsername());
            throw new Exception("Invalid username or password");
        }

        return buildLoginResponse(user);
    }

    private LoginResponse buildLoginResponse(User user) {
        LoginResponse response = new LoginResponse();
        response.setStatus("SUCCESS");
        response.setMessage("Login successful");
        response.setRole(user.getRole());
        response.setUserId(String.valueOf(user.getId()));
        response.setAvatar(user.getAvatar());
        response.setCompanyId(user.getOrgId());
        response.setCompanyName(user.getOrgName());
        response.setUserName(user.getFirstName() + " " + user.getLastName());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setLogin_ts(System.currentTimeMillis());

        try {
            String token = generateToken(user);
            response.setToken(token);
            response.setTokenExpiryTs(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY);
            response.setRefreshToken("dummy-refresh-token");
            response.setRefreshTokenExpiryTs(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRY);
        } catch (JOSEException e) {
            log.error("JWT Generation failed: {}", e.getMessage());
            response.setToken("error-generating-token");
        }

        return response;
    }

    private String generateToken(User user) throws JOSEException {
        JWSSigner signer = new MACSigner(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date exp = new Date(now.getTime() + ACCESS_TOKEN_EXPIRY);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(String.valueOf(user.getId()))
                .issuer("varista-service")
                .issueTime(now)
                .expirationTime(exp)
                .claim("email", user.getEmail())
                .claim("role", user.getRole())
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signedJWT.sign(signer);
        return signedJWT.serialize();
    }

    public void resetPassword(String emailId) {
        userRepository.findByEmail(emailId).ifPresentOrElse(
                user -> emailService.sendPasswordResetEmail(user.getEmail(), user.getPassword()),
                () -> log.error("Reset password failed: Email {} not found", emailId)
        );
    }

    @Transactional
    public User updateUser(UserRequest request) {
        log.info("Update request for User {} starts",request.getId());
        return userRepository.findById(request.getId()).map(user -> {
            user.setEmail(request.getEmail());
            // Ideally encode this
            user.setPhone(request.getPhone());
            user.setAvatar(request.getAvatar());
            if (!request.getPassword().isEmpty() && !request.getPassword().equals(user.getPassword()))
                user.setPassword(request.getPassword());
            return userRepository.save(user);
        }).orElseThrow(() -> new RuntimeException("User not found with id: " + request.getId()));
    }
}