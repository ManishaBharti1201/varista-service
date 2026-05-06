package com.varista.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Data;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Data
public class Employee {
    @Id
    private Long id;
    private String firstName;
    private String lastName;
    private String fatherName;
    private String gender;
    private String email;
    private String phone;
    private String role;
    private Long adhaar;
    private Long eip;
    private Long esic;
    private Long uan;
    private LocalDate dob;
    private LocalDate joiningDate;
    @ColumnDefault("false")
    private Boolean timesheetPresent;
    @ColumnDefault("false")
    private Boolean salaryPresent;
    private LocalDate createdAt;
    private String weeklyDaysOff;
    private String category;
}
