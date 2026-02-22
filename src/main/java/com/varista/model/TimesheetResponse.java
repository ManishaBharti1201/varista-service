package com.varista.model;

import com.varista.entity.Attendance;
import lombok.Data;

import java.util.List;

@Data
public class TimesheetResponse {
    private long employeeId;
    private double workingHrs;
    private String weeklyOffs;
    private long leavesCount;
    private long workdaysCount;
    private String monthName;
    private List<Attendance> monthlyTimesheet;
}
