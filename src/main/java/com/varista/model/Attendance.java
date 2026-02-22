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

    public void setCheckIn(String checkIn) {
        this.checkIn = checkIn;
    }

    public String getCheckIn() {
        return checkIn;
    }

    public void setCheckOut(String checkOut) {
        this.checkOut = checkOut;
    }

    public String getCheckOut() {
        return checkOut;
    }

    public void setWorkHours(String workHours) {
        this.workHours = workHours;
    }

    public String getWorkHours() {
        return workHours;
    }
}
