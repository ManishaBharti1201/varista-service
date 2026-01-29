package com.varista.controller;

import com.varista.model.LoginRequest;
import com.varista.model.LoginResponse;
import com.varista.service.InvoiceService;
import com.varista.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@CrossOrigin(origins = "*") // Allow all origins

public class UserController {

    @Autowired
    InvoiceService invoiceService;

    @Autowired
    UserService userService;

    @GetMapping("/health")
    public String healthCheck() {
        return "Customer Service is up and running!";
    }

    @PostMapping("/login")
    public LoginResponse login( @RequestBody LoginRequest loginRequest) {
        return userService.login(loginRequest);
    }

    @PostMapping("/register")
    public LoginResponse register( @RequestBody LoginRequest loginRequest) {
        return userService.register(loginRequest);
    }

}
