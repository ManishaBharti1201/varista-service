package com.varista.service;

import com.varista.entity.Attendance;
import com.varista.entity.Employee;
import com.varista.model.MusterWageInfo; // Changed import from MusterSheetInfo
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@Slf4j
public class MusterDataCalculator {

    @Autowired
    EmployeeService employeeService;

    public void calculateMusterSheetData(List<Attendance> attendanceList, List<MusterWageInfo> musterWageInfos) { // Changed List<MusterSheetInfo> to List<MusterWageInfo>
        log.info("Starting muster sheet data calculation for {} attendance records.", attendanceList.size());
        try {
            List<Employee> employeeList = employeeService.getAll();
            log.debug("Retrieved {} employees for muster sheet calculation.", employeeList.size());

            final int[] count = {0};
            employeeList.forEach(employee -> {
                log.debug("Processing employee: {} (ID: {})", employee.getFirstName() + " " + employee.getLastName(), employee.getId());
                MusterWageInfo musterWageInfo = new MusterWageInfo(); // Changed MusterSheetInfo to MusterWageInfo

                // Map Employee data to MusterWageInfo
                musterWageInfo.setSerialNo(++count[0]);
                musterWageInfo.setEmployeeName(employee.getFirstName() + " " + employee.getLastName());
                musterWageInfo.setFatherName(employee.getFatherName()); // Assuming Employee has getFatherName()
                musterWageInfo.setGender(employee.getGender()); // Assuming Employee has getGender()
                musterWageInfo.setDesignation(employee.getRole());
                musterWageInfo.setCategory(employee.getCategory()); // Assuming Employee has getCategory()

                if (employee.getDob() != null) {
                    musterWageInfo.setDateOfBirth(employee.getDob());
                } else {
                    log.warn("Employee ID {} has no Date of Birth.", employee.getId());
                }

                if (employee.getAdhaar() != null) {
                    musterWageInfo.setAadharNumber(String.valueOf(employee.getAdhaar()));
                } else {
                    log.warn("Employee ID {} has no Aadhar Number.", employee.getId());
                }

                if (employee.getEip() != null) {
                    musterWageInfo.setEipId(employee.getEip().toString());
                } else {
                    log.warn("Employee ID {} has no EIP.", employee.getId());
                }

                if (employee.getJoiningDate() != null) {
                    musterWageInfo.setDateOfJoining(employee.getJoiningDate());
                } else {
                    log.warn("Employee ID {} has no Joining Date.", employee.getId());
                }

                // Statutory Identifiers (assuming Employee has these fields)
                musterWageInfo.setUanNo(String.valueOf(employee.getUan())); // Assuming Employee has getUanNo()
                musterWageInfo.setEsiNo(String.valueOf(employee.getEsic())); // Assuming Employee has getEsiNo()

                // Attendance/Muster Specifics
                Map<Integer, String> dailyAttendanceStatus = new HashMap<>(); // Changed timesheet to dailyAttendanceStatus
                attendanceList.stream()
                        .filter(attendance -> Objects.equals(attendance.getMasterEmployeeId(), employee.getId()))
                        .forEach(result -> {
                            dailyAttendanceStatus.put(result.getAttendDate().getDayOfMonth(), result.getStatus());
                        });
                musterWageInfo.setDailyAttendanceStatus(dailyAttendanceStatus); // Mapped to new field
                musterWageInfo.setWorkingHours("Day Shift"); // Default value
                musterWageInfo.setInterval("1 Hr");     // Default value

                // Financial/Wage Details (initialized to 0.0 or null as no source in current context)
                musterWageInfo.setDailyWage(0.0);
                musterWageInfo.setBasicWages(0.0);
                musterWageInfo.setOtAmount(0.0);
                musterWageInfo.setNhAmount(0.0);
                musterWageInfo.setHra(0.0);
                musterWageInfo.setGrossWage(0.0);
                musterWageInfo.setPfDeduction(0.0);
                musterWageInfo.setEsicDeduction(0.0);
                musterWageInfo.setPtDeduction(0.0);
                musterWageInfo.setLwfDeduction(0.0);
                musterWageInfo.setOtherDeductions(0.0);
                musterWageInfo.setTotalDeductions(0.0);
                musterWageInfo.setNetWage(0.0);
                musterWageInfo.setDateOfPayment(null);
                musterWageInfo.setJobCode(null);
                musterWageInfo.setRemarks(null);
                musterWageInfo.setSubContractorName("Varista Industrial Solutions"); // Mapped contractorName to subContractorName

                calculateTotalOTAndHNDays(attendanceList.stream()
                        .filter(attendance -> Objects.equals(attendance.getMasterEmployeeId(), employee.getId()))
                        .toList(), musterWageInfo); // Changed MusterSheetInfo to MusterWageInfo

                musterWageInfos.add(musterWageInfo); // Changed musterSheetInfos to musterWageInfos
                log.debug("MusterWageInfo created for employee: {}", musterWageInfo.getEmployeeName());
            });

            // Re-index serialNo after all items are added
            for (int i = 0; i < musterWageInfos.size(); i++) {
                musterWageInfos.get(i).setSerialNo(i + 1);
            }
            log.info("Muster sheet data calculation completed. Generated {} muster wage infos.", musterWageInfos.size());
        } catch (Exception e) {
            log.error("Error calculating muster sheet data: {}", e.getMessage(), e);
            throw e; // Re-throw to ensure upstream handling
        }
    }

    private void calculateTotalOTAndHNDays(List<Attendance> attendanceStream, MusterWageInfo musterWageInfo) { // Changed MusterSheetInfo to MusterWageInfo
        log.debug("Calculating total OT and NH days for employee: {}", musterWageInfo.getEmployeeName());
        double totalDaysWorked = 0; // Changed to double to match MusterWageInfo
        double overTimeHours = 0; // Changed to double to match MusterWageInfo
        double nightHalt = 0; // Changed to double to match MusterWageInfo

        for (Attendance dayEntry : attendanceStream) {
            String status = dayEntry.getStatus();
            log.trace("Processing attendance entry for date {} with status {}", dayEntry.getAttendDate(), status);

            // 1. Calculate Total Days Worked
            if (status != null && status.matches("P|AP|NH|PH|OT.*")) {
                totalDaysWorked++;
            }

            // 2. Calculate OT Hours for working days
            if (status != null && (status.contains("OT") || status.equals("AP") || status.equals("P"))) {
                double dailyOt = calculateDailyOT(dayEntry);
                overTimeHours += dailyOt;
                log.trace("Daily OT for {} is {}. Current total OT: {}", dayEntry.getAttendDate(), dailyOt, overTimeHours);
            }

            // 3. Calculate NH Hours (Holiday Pay logic)
            if (status != null && (status.equals("NH") || status.equals("PH"))) {
                nightHalt += 1.0;
                log.trace("NH/PH day for {}. Current total NH: {}", dayEntry.getAttendDate(), nightHalt);
            }
        }

        musterWageInfo.setTotalDaysWorked(totalDaysWorked); // Mapped to new field
        musterWageInfo.setOverTimeHours(overTimeHours);     // Mapped to new field
        musterWageInfo.setNightHalt(nightHalt);             // Mapped to new field
        log.debug("Finished calculating OT and NH days for {}. Total Days: {}, OT Hours: {}, NH Hours: {}",
                musterWageInfo.getEmployeeName(), totalDaysWorked, overTimeHours, nightHalt);
    }

    private double calculateDailyOT(Attendance entry) {
        log.debug("Calculating daily OT for attendance entry on date: {}", entry.getAttendDate());
        try {
            LocalTime actualInTime = LocalTime.parse(entry.getInTime());
            LocalTime outTime = LocalTime.parse(entry.getOutTime());
            log.debug("InTime: {}, OutTime: {}", actualInTime, outTime);

            // Default to 9:30 if arriving early
            LocalTime effectiveInTime = actualInTime.isBefore(LocalTime.of(9, 30))
                    ? LocalTime.of(9, 30)
                    : actualInTime;
            log.debug("Effective InTime (after 9:30 adjustment): {}", effectiveInTime);

            // Calculate duration in hours
            double duration = Duration.between(effectiveInTime, outTime).toMinutes() / 60.0;
            log.debug("Calculated duration: {} hours", duration);

            // Subtract 1 hour for lunch
            double otHours = duration - 1.0;
            log.debug("OT hours before threshold: {} hours", otHours);

            // Threshold check: Must be at least 1 hour
            double finalOt = (otHours >= 1.0) ? otHours : 0;
            log.debug("Final daily OT for {}: {} hours", entry.getAttendDate(), finalOt);
            return finalOt;
        } catch (DateTimeParseException e) {
            log.error("Failed to parse time for OT calculation for employee {} on {}. InTime: {}, OutTime: {}. Error: {}",
                    entry.getEmployeeName(), entry.getAttendDate(), entry.getInTime(), entry.getOutTime(), e.getMessage());
            return 0; // Return 0 OT if time parsing fails
        } catch (Exception e) {
            log.error("Error calculating daily OT for employee {} on {}. Error: {}",
                    entry.getEmployeeName(), entry.getAttendDate(), e.getMessage(), e);
            return 0;
        }
    }
}
