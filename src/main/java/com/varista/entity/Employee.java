package com.varista.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Data;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;

@Entity
@Data
public class Employee {
    @Id
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String role;
    private String adhaar;
    private LocalDate joiningDate;
    @ColumnDefault("false")
    private Boolean timesheetPresent;
    @ColumnDefault("false")
    private Boolean salaryPresent;
    private LocalDate createdAt;
}
