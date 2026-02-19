package com.varista.model;

import lombok.Data;

@Data
public class UserRequest {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private String phone;
    private String orgName;
    private String role;
    private String avatar;
}
