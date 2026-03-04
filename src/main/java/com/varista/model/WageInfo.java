package com.varista.model;

import java.time.LocalDate;
import java.util.Map;
import lombok.Data;

/**
 * Model representing consolidated data from Muster Register and Wage Register.
 */
@Data
public class WageInfo {
    // Basic Information
    private int serialNo;
    private String aadharNumber;
    private String eipId; // Also known as EIP Number
    private String employeeName;
    private String fatherName;
    private String gender;
    private String designation;
    private String category;

    // Dates
    private LocalDate dateOfBirth;
    private LocalDate dateOfJoining; // DOJ

    // Statutory Identifiers
    private String uanNo;
    private String esiNo;

    // Attendance/Muster Specifics
    private String workingHours; // e.g., "Day Shift"
    private String interval;     // e.g., "1 Hr"
    /**
     * Maps day of month (1-31) to attendance status (P, A, L, etc.)
     */
    private double totalDaysWorked;
    private double overTimeHours;
    private double nightHalt; // NH

    // Financial/Wage Details
    private double dailyWage;
    private double basicWages;
    private double otAmount;
    private double nhAmount;
    private double hra;
    private double grossWage;

    // Deductions
    private double pfDeduction;
    private double esicDeduction;
    private double ptDeduction; // Professional Tax
    private double lwfDeduction; // Labour Welfare Fund
    private double otherDeductions;
    private double totalDeductions;

    // Payment
    private double netWage;
    private LocalDate dateOfPayment;

    // Project Metadata
    private String jobCode;
    private String subContractorName; // S/c Name
    private String remarks;
}