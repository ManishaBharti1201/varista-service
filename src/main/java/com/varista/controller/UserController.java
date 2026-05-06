package com.varista.controller;

import com.varista.entity.User;
import com.varista.model.LoginRequest;
import com.varista.model.LoginResponse;
import com.varista.model.UserRequest;
import com.varista.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map; // Keep this import as verifyOtp might be uncommented later

@Slf4j
@RestController
@RequestMapping("/user")
@CrossOrigin(origins = "*")
@Tag(name = "User Authentication & Profile", description = "Endpoints for user login, password recovery, and profile updates")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
        log.info("UserController initialized.");
    }

    @GetMapping("/health")
    @Operation(summary = "Service health check", description = "Simple heartbeat to verify user service availability")
    public String healthCheck() {
        log.info("Received request for user service health check.");
        return "Customer Service is up and running!";
    }

    @PutMapping("/update")
    @Operation(summary = "Update user profile", description = "Updates user contact information or credentials based on User ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User updated successfully",
                    content = @Content(schema = @Schema(implementation = User.class))),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<User> updateUser(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Updated user details")
            @RequestBody UserRequest request) {
        log.info("Received request to update user: {}", request);
        User result = userService.updateUser(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/login")
    @Operation(summary = "User Login", description = "Authenticates user and returns JWT token plus organization context")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authentication successful",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid username or password"),
            @ApiResponse(responseCode = "403", description = "Account locked or forbidden access")
    })
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            log.info("Received request for login user: {}", loginRequest.getUsername());
            return ResponseEntity.ok().body(userService.login(loginRequest));
        } catch (Exception e) {
            log.error("Login failed for {}: {}", loginRequest.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }


    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> request) {
        log.info("Received request to verify OTP for request: {}", request);
        String firebaseToken = request.get("firebaseToken");
        Long userId = Long.parseLong( request.get("userId"));

        try {
            // 1. Verify the token with Firebase
            // FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(firebaseToken);
            // String phoneNumber = (String) decodedToken.getClaims().get("phone_number"); // Access phone number from claims

            // if (phoneNumber == null) {
            //     return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Phone number not found in token claims");
            // }

            // 2. Cross-verify with your DB
            // User user = userService.getUser( userId);
            // if (user != null && user.getPhone().equals(phoneNumber)) {
            //     // 3. Success: Generate your app's final login response
            //     return ResponseEntity.ok(userService.generateLoginResponse(user));
            // }
            // return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Phone number mismatch");
            log.warn("Verify OTP endpoint is currently commented out and not functional.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Verify OTP functionality is temporarily disabled.");
        } catch (Exception e) { // Changed from FirebaseAuthException to Exception to catch potential parsing errors
            log.error("Error during OTP verification: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid OTP token or verification error");
        }
    }

    @PostMapping("/resetPassword/{emailId}")
    @Operation(summary = "Trigger password reset", description = "Sends a password recovery email to the specified address")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reset email sent successfully (or process triggered)"),
            @ApiResponse(responseCode = "404", description = "Email address not found")
    })
    public ResponseEntity<Void> resetPassword(
            @Parameter(description = "Registered email address of the user", example = "john.doe@jsw.in")
            @PathVariable String emailId) {
        log.info("Received request to reset password for email: {}", emailId);
        userService.resetPassword(emailId);
        return ResponseEntity.ok().build();
    }
}