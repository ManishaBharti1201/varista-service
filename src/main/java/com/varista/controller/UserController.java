package com.varista.controller;

import com.varista.model.LoginRequest;
import com.varista.model.LoginResponse;
import com.varista.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@CrossOrigin(origins = "*")
@Tag(name = "User Authentication", description = "Endpoints for user login, registration, and session management")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/health")
    @Operation(summary = "User service health check", description = "Checks if the authentication and user management service is active")
    public String healthCheck() {
        return "Customer Service is up and running!";
    }

    @PostMapping("/login")
    @Operation(
            summary = "User Login",
            description = "Authenticates a user with credentials and returns a session token or success status",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Authentication successful",
                            content = @Content(schema = @Schema(implementation = LoginResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Invalid username or password")
            }
    )
    public LoginResponse login(@RequestBody LoginRequest loginRequest) {
        return userService.login(loginRequest);
    }

    @PostMapping("/register")
    @Operation(
            summary = "User Registration",
            description = "Creates a new user account in the system and returns initial login details",
            responses = {
                    @ApiResponse(responseCode = "201", description = "User created successfully"),
                    @ApiResponse(responseCode = "409", description = "User already exists")
            }
    )
    public LoginResponse register(@RequestBody LoginRequest loginRequest) {
        return userService.register(loginRequest);
    }
}
