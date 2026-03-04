package com.varista.model;

import lombok.Data;

import java.util.Map;

@Data
public class MusterSheetInfo {
    private Integer slNo;
    private String adhaar;
    private String eip;
    private String employeeName;
    private String designation;
    private String dob;
    private String dateOfEntry;
    private String workingHrs = "Day Shift";
    private String interval = "1 Hr";
    private Map<Integer, String> calender;
    private Long totalDays = 0L;
    private Long otHrs = 0L;
    private Long nhHrs = 0L;
    private String jobCode;
    private String contractorName;

}
