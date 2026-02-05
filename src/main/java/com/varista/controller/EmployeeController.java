package com.varista.controller;

import com.varista.model.LoginRequest;
import com.varista.model.LoginResponse;
import com.varista.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/employee")
@CrossOrigin(origins = "*")
@Tag(name = "Employee Authentication", description = "Endpoints for employee login, registration, and session management")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping("/health")
    @Operation(summary = "Employee service health check", description = "Checks if the authentication and employee management service is active")
    public String healthCheck() {
        return "Customer Service is up and running!";
    }

    @PostMapping("/login")
    @Operation(
            summary = "Employee Login",
            description = "Authenticates a employee with credentials and returns a session token or success status",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Authentication successful",
                            content = @Content(schema = @Schema(implementation = LoginResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Invalid employeename or password")
            }
    )
    public LoginResponse login(@RequestBody LoginRequest loginRequest) {
        return null;
    }

    @PostMapping("/register")
    @Operation(
            summary = "Employee Registration",
            description = "Creates a new employee account in the system and returns initial login details",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Employee created successfully"),
                    @ApiResponse(responseCode = "409", description = "Employee already exists")
            }
    )
    public LoginResponse register(@RequestBody LoginRequest loginRequest) {
       // return employeeService.register(loginRequest);
        return null;
    }
}
