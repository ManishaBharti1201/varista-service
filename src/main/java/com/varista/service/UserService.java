package com.varista.service;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.varista.entity.PasswordResetToken;
import com.varista.entity.User;
import com.varista.model.LoginRequest;
import com.varista.model.LoginResponse;
import com.varista.model.PasswordResetRequest;
import com.varista.model.UserRequest;
import com.varista.repository.PasswordResetTokenRepository;
import com.varista.repository.UserRepository;
import com.varista.tenant.TenantContext;
import com.varista.utils.EmailUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Slf4j
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailUtils emailService;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    private static final long ACCESS_TOKEN_EXPIRY = 3600_000; // 1 hour
    private static final long REFRESH_TOKEN_EXPIRY = 86400_000; // 24 hours
    private static final int PASSWORD_RESET_CODE_LENGTH = 6;
    private static final int PASSWORD_RESET_CODE_EXPIRY_MINUTES = 10;
    private static final int PASSWORD_RESET_RESEND_DELAY_SECONDS = 60;
    private static final int MAX_PASSWORD_RESET_ATTEMPTS = 5;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public LoginResponse login(LoginRequest loginRequest) throws Exception {
        log.info("Attempting login for user: {}", loginRequest.getUsername());

        Long selectedCompanyId = TenantContext.getTenantId();
        if (selectedCompanyId == null) {
            throw new Exception("Invalid username or password");
        }

        User user = userRepository.findByEmail(loginRequest.getUsername())
                .orElseThrow(() -> {
                    log.warn("Login failed: User {} not found.", loginRequest.getUsername());
                    return new Exception("Invalid username or password");
                });

        if (user.getOrgId() != selectedCompanyId) {
            throw new Exception("Invalid username or password");
        }

        // Security Note: In production, use BCryptPasswordEncoder.matches(raw, encoded)
        if (!loginRequest.getPassword().equals(user.getPassword())) {
            log.warn("Password mismatch for user: {}", loginRequest.getUsername());
            throw new Exception("Invalid username or password");
        }
        log.info("User {} authenticated successfully.", loginRequest.getUsername());
        return generateLoginResponse(user);
    }

    public LoginResponse generateLoginResponse(User user) {
        log.info("Generating login response for user ID: {}", user.getId());
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
            log.info("Login response generated with token for user ID: {}", user.getId());
        } catch (JOSEException e) {
            log.error("JWT Generation failed for user ID {}: {}", user.getId(), e.getMessage());
            response.setToken("error-generating-token");
        }

        return response;
    }

    private String generateToken(User user) throws JOSEException {
        log.info("Generating JWT token for user ID: {}", user.getId());
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
                .claim("company_id", String.valueOf(user.getOrgId()))
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signedJWT.sign(signer);
        log.info("JWT token generated for user ID: {}", user.getId());
        return signedJWT.serialize();
    }

    @Transactional
    public void requestPasswordReset(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant is required for password reset.");
        }

        userRepository.findByEmailIgnoreCaseAndOrgId(email, tenantId).ifPresent(user -> {
            Instant now = Instant.now();
            boolean resendCoolingDown = passwordResetTokenRepository
                    .findTopByEmailIgnoreCaseOrderByCreatedAtDesc(email)
                    .map(token -> token.getCreatedAt().isAfter(now.minusSeconds(PASSWORD_RESET_RESEND_DELAY_SECONDS)))
                    .orElse(false);
            if (resendCoolingDown) {
                return;
            }

            String verificationCode = generateVerificationCode();
            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setEmail(email);
            resetToken.setCodeHash(hashVerificationCode(verificationCode));
            resetToken.setCreatedAt(now);
            resetToken.setExpiresAt(resetToken.getCreatedAt().plusSeconds(PASSWORD_RESET_CODE_EXPIRY_MINUTES * 60L));
            resetToken.setFailedAttempts(0);

            passwordResetTokenRepository.deleteByEmailIgnoreCase(email);
            passwordResetTokenRepository.save(resetToken);
            emailService.sendPasswordResetCode(user.getEmail(), verificationCode);
        });
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void confirmPasswordReset(PasswordResetRequest request) {
        String email = normalizeEmail(request.getEmail());
        String verificationCode = request.getVerificationCode();
        String newPassword = request.getNewPassword();
        if (verificationCode == null || !verificationCode.matches("\\d{" + PASSWORD_RESET_CODE_LENGTH + "}")) {
            throw new IllegalArgumentException("Invalid or expired verification code.");
        }
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be at least 8 characters long.");
        }

        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant is required for password reset.");
        }

        PasswordResetToken resetToken = passwordResetTokenRepository
                .findTopByEmailIgnoreCaseOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification code."));

        if (!resetToken.getExpiresAt().isAfter(Instant.now())
                || resetToken.getFailedAttempts() >= MAX_PASSWORD_RESET_ATTEMPTS) {
            passwordResetTokenRepository.delete(resetToken);
            throw new IllegalArgumentException("Invalid or expired verification code.");
        }

        byte[] expectedHash = Base64.getDecoder().decode(resetToken.getCodeHash());
        byte[] actualHash = Base64.getDecoder().decode(hashVerificationCode(verificationCode));
        if (!MessageDigest.isEqual(expectedHash, actualHash)) {
            resetToken.setFailedAttempts(resetToken.getFailedAttempts() + 1);
            if (resetToken.getFailedAttempts() >= MAX_PASSWORD_RESET_ATTEMPTS) {
                passwordResetTokenRepository.delete(resetToken);
            } else {
                passwordResetTokenRepository.save(resetToken);
            }
            throw new IllegalArgumentException("Invalid or expired verification code.");
        }

        User user = userRepository.findByEmailIgnoreCaseAndOrgId(email, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification code."));
        user.setPassword(newPassword);
        userRepository.save(user);
        passwordResetTokenRepository.delete(resetToken);
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email address is required.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String generateVerificationCode() {
        int bound = (int) Math.pow(10, PASSWORD_RESET_CODE_LENGTH);
        return String.format(Locale.ROOT, "%0" + PASSWORD_RESET_CODE_LENGTH + "d", SECURE_RANDOM.nextInt(bound));
    }

    private String hashVerificationCode(String verificationCode) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(verificationCode.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to process password reset verification code.", exception);
        }
    }

    @Transactional
    public User updateUser(UserRequest request) {
        log.info("Attempting to update user with ID: {}", request.getId());
        return userRepository.findById(request.getId()).map(user -> {
            user.setEmail(request.getEmail());
            user.setPhone(request.getPhone());
            user.setAvatar(request.getAvatar());
            if (!request.getPassword().isEmpty() && !request.getPassword().equals(user.getPassword())) {
                user.setPassword(request.getPassword());
                log.info("User ID: {} password updated.", user.getId());
            }
            User updatedUser = userRepository.save(user);
            log.info("User ID: {} updated successfully.", updatedUser.getId());
            return updatedUser;
        }).orElseThrow(() -> {
            log.error("User not found with id: {} for update.", request.getId());
            return new RuntimeException("User not found with id: " + request.getId());
        });
    }


    @Transactional
    public User getUser(Long id){
        log.info("Fetching user with ID: {}", id);
        return userRepository.findById(id).orElseThrow(()
                -> {
            log.error("User not found with id: {}", id);
            return new RuntimeException("User not found with id: " + id);
        });
    }
}
