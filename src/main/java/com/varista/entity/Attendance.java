package com.varista.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Data
public class Attendance {
    @Id
    @GeneratedValue
    private Long attendanceId;
    private Long masterEmployeeId;
    private String employeeId;
    private String employeeName;
    private String company;
    private LocalDate attendDate;
    private String inTime;
    private String outTime;
    private String totalHours;
    private String status;
    private LocalDateTime updatedAt = LocalDateTime.now();
}
