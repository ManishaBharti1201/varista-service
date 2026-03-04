package com.varista.service;

import com.varista.entity.Attendance;
import com.varista.entity.Employee;
import com.varista.model.MusterSheetInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Component
@Slf4j
public class MusterDataCalculator {

    @Autowired
    EmployeeService employeeService;

    public void calculateMusterSheetData(List<Attendance> attendanceList, List<MusterSheetInfo> musterSheetInfos) {

        try {
            List<Employee> employeeList = employeeService.getAll();

            final int count = 0;
            employeeList.forEach(employee -> {
                MusterSheetInfo musterSheetInfo = new MusterSheetInfo();
                musterSheetInfo.setSlNo(count);
                musterSheetInfo.setEmployeeName(employee.getFirstName() + " " + employee.getLastName());
                musterSheetInfo.setDob(employee.getDob().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                musterSheetInfo.setAdhaar(String.valueOf(employee.getAdhaar()));
                musterSheetInfo.setDesignation(employee.getRole());
                musterSheetInfo.setEip(employee.getEip().toString());
                musterSheetInfo.setDateOfEntry(employee.getJoiningDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                Map<Integer, String> timesheet = new HashMap<>();

                attendanceList.stream()
                        .filter(attendance -> Objects.equals(attendance.getMasterEmployeeId(), employee.getId()))
                        .forEach(result -> {
                            timesheet.put(result.getAttendDate().getDayOfMonth(), result.getStatus());

                        });
                musterSheetInfo.setCalender(timesheet);
                musterSheetInfo.setContractorName("Varista Industrial Solutions");
                calculateTotalOTAndHNDays(attendanceList.stream()
                        .filter(attendance -> Objects.equals(attendance.getMasterEmployeeId(), employee.getId()))
                        .toList(), musterSheetInfo);
                musterSheetInfos.add(musterSheetInfo);
            });




            for (int i = 1; i <= employeeList.size(); i++) {
                musterSheetInfos.get(i - 1).setSlNo(i);
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    private void calculateTotalOTAndHNDays(List<Attendance> attendanceStream, MusterSheetInfo musterSheetInfo) {

        long totalDays = 0;
        double totalOtHours = 0;
        double totalNhHours = 0;

        for (Attendance dayEntry : attendanceStream) {
            String status = dayEntry.getStatus();

            // 1. Calculate Total Days
            if (status.matches("P|AP|NH|PH|OT.*")) {
                totalDays++;
            }

            // 2. Calculate OT Hours for working days
            if (status.contains("OT") || status.equals("AP") || status.equals("P")) {
                double dailyOt = calculateDailyOT(dayEntry); // Logic below
                totalOtHours += dailyOt;
            }

            // 3. Calculate NH Hours (Holiday Pay logic)
            if (status.equals("NH") || status.equals("PH")) {
                // Per image: 5 days of NH (absent) = 5 NH-O hours
                totalNhHours += 1.0;
            }
        }

        musterSheetInfo.setTotalDays(totalDays);
        musterSheetInfo.setOtHrs((long) totalOtHours);
        musterSheetInfo.setNhHrs((long) totalNhHours);

    }

    private double calculateDailyOT(Attendance entry) {
        // Assuming you extract LocalTime from your data source
        LocalTime actualInTime = LocalTime.parse(entry.getInTime());
        LocalTime outTime = LocalTime.parse(entry.getOutTime());

        // Default to 9:30 if arriving early
        LocalTime effectiveInTime = actualInTime.isBefore(LocalTime.of(9, 30))
                ? LocalTime.of(9, 30)
                : actualInTime;

        // Calculate duration in hours
        double duration = Duration.between(effectiveInTime, outTime).toMinutes() / 60.0;

        // Subtract 1 hour for lunch
        double otHours = duration - 1.0;

        // Threshold check: Must be at least 1 hour
        return (otHours >= 1.0) ? otHours : 0;
    }


}
