package com.varista.model;

import lombok.Data;
import lombok.Setter;

@Data
public class Attendance {
    private String date;
    private String status;
    private String checkIn;
    private String checkOut;
    private String workHours;
}
