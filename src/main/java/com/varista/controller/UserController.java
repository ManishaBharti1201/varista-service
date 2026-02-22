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

@Slf4j
@RestController
@RequestMapping("/user")
@CrossOrigin(origins = "*")
@Tag(name = "User Authentication & Profile", description = "Endpoints for user login, password recovery, and profile updates")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/health")
    @Operation(summary = "Service health check", description = "Simple heartbeat to verify user service availability")
    public String healthCheck() {
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
        log.info("received request for Update user {}", request);
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
            log.info("Received request for login user {}", loginRequest.getUsername());
            return ResponseEntity.ok().body(userService.login(loginRequest));
        } catch (Exception e) {
            log.error("Login failed for {}: {}", loginRequest.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
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
        userService.resetPassword(emailId);
        return ResponseEntity.ok().build();
    }
}