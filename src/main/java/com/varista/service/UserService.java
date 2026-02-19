package com.varista.service;

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

import java.time.LocalDate;
import java.util.Date;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

@Slf4j
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailUtils emailService;

    @Value("${security.jwt.secret:change-it-in-production}")
    private String jwtSecret;

    public LoginResponse login(LoginRequest loginRequest) {
        log.info("Login request starts.. {}",loginRequest);
        LoginResponse loginResponse = new LoginResponse();
        userRepository.findByEmail(loginRequest.getUsername())
                .ifPresentOrElse(user -> {
                            if (loginRequest.getPassword().equals(user.getPassword())) {
                                loginResponse.setStatus("SUCCESS");
                                loginResponse.setMessage("Login successful");
                                loginResponse.setRole(user.getRole());
                                loginResponse.setUserId(String.valueOf(user.getId()));
                                loginResponse.setAvatar(user.getAvatar());
                                loginResponse.setCompanyId(user.getOrgId());
                                loginResponse.setCompanyName(user.getOrgName());
                                loginResponse.setLogin_ts(System.currentTimeMillis());

                                // Generate HS256 JWT signed with security.jwt.secret
                                try {
                                    byte[] secretBytes = jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                                    JWSSigner signer = new MACSigner(secretBytes);

                                    long now = System.currentTimeMillis();
                                    Date iat = new Date(now);
                                    Date exp = new Date(now + 3600_000); // 1 hour expiry

                                    JWTClaimsSet claims = new JWTClaimsSet.Builder()
                                            .subject(String.valueOf(user.getId()))
                                            .issuer("varista-service")
                                            .issueTime(iat)
                                            .expirationTime(exp)
                                            .claim("email", user.getEmail())
                                            .claim("role", user.getRole())
                                            .build();

                                    SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
                                    signedJWT.sign(signer);

                                    String token = signedJWT.serialize();

                                    loginResponse.setToken(token);
                                    loginResponse.setRefreshToken("dummy-refresh-token");
                                    loginResponse.setTokenExpiryTs(exp.getTime()); // millis
                                    loginResponse.setRefreshTokenExpiryTs(System.currentTimeMillis() + 86400000); // 24 hours expiry

                                } catch (JOSEException e) {
                                    log.error("Error generating JWT token: {}", e.getMessage(), e);
                                    // fallback to dummy token if signing fails
                                    loginResponse.setToken("dummy-jwt-token");
                                }

                                loginResponse.setUserName(user.getFirstName() + " " + user.getLastName());
                                loginResponse.setEmail(user.getEmail());
                                loginResponse.setPhone(user.getPhone());
                                loginResponse.setFirstName(user.getFirstName());
                                loginResponse.setLastName(user.getLastName());
                            }


                        },
                        () -> {
                            // This block executes if the email is NOT found in the database
                            loginResponse.setStatus("FAILED");
                            loginResponse.setMessage("Login unsuccessful");
                            log.error("Error: The email address {} does not exist in our records.", loginRequest.getUsername());
                        }

                );
        return loginResponse;
    }

    public LoginResponse register(LoginRequest loginRequest) {
        return null;
    }

    public void resetPassword(String emailId) {

        userRepository.findByEmail(emailId)
                .ifPresentOrElse(
                        user -> {
                            emailService.sendPasswordResetEmail(user.getEmail(), user.getPassword());
                        },
                        () -> {
                            // This block executes if the email is NOT found in the database
                            log.error("Error: The email address {} does not exist in our records.", emailId);
                        }
                );


    }

    public User updateUser(UserRequest request) {
      return   userRepository.findById(request.getId()).map(user -> {
            user.setEmail(request.getEmail());
            user.setPassword(request.getPassword());
            user.setPhone(request.getPhone());
            user.setAvatar(request.getAvatar());
            //user.setOrgName(request.getOrgName());
            //user.setRole(request.getRole());
            user.setLastUpdateDate(LocalDate.now()); // Assuming you want to update the timestamp

            // Save and return the updated user
            return userRepository.save(user);
        }).orElseThrow(() -> new RuntimeException("User not found with id: " + request.getId()));
    }
}
