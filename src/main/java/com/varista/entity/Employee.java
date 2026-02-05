package com.varista.entity;

import jakarta.persistence.Entity;
import lombok.Data;

import java.time.LocalDate;

@Entity
@Data
public class Employee {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String position;
    private String role;
    private String adhaar;
    private LocalDate joiningDate;
    private LocalDate createdAt;
}
