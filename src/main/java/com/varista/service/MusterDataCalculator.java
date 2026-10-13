package com.varista.service;

import com.varista.entity.Attendance;
import com.varista.entity.Employee;
import com.varista.model.MusterWageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class MusterDataCalculator {

    private static final int SHIFT_HOURS = 8;
    private static final int BREAK_HOURS = 1;
    private static final Set<String> ABSENCE_STATUSES = Set.of("A", "AB");
    private static final Set<String> NATIONAL_HOLIDAY_STATUSES = Set.of("N/H", "NH", "PH");
    private static final DateTimeFormatter[] TIME_FORMATTERS = {
            DateTimeFormatter.ISO_LOCAL_TIME,
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH)
    };

    private final EmployeeService employeeService;
    private final PayrollCalculator payrollCalculator;
    private final double partialDayCredit;
    private final double exceptionDayCredit;

    public MusterDataCalculator(
            EmployeeService employeeService,
            PayrollCalculator payrollCalculator,
            @Value("${payroll.attendance.partial-day-credit:0.0}") double partialDayCredit,
            @Value("${payroll.attendance.exception-day-credit:0.0}") double exceptionDayCredit) {
        if (!Double.isFinite(partialDayCredit) || partialDayCredit < 0 || partialDayCredit > 1
                || !Double.isFinite(exceptionDayCredit) || exceptionDayCredit < 0 || exceptionDayCredit > 1) {
            throw new IllegalArgumentException("Attendance day credits must be between 0 and 1.");
        }
        this.employeeService = employeeService;
        this.payrollCalculator = payrollCalculator;
        this.partialDayCredit = partialDayCredit;
        this.exceptionDayCredit = exceptionDayCredit;
    }

    public void calculateMusterSheetData(List<Attendance> attendanceList, List<MusterWageInfo> musterWageInfos) {
        log.info("Starting muster sheet data calculation for {} attendance records.", attendanceList.size());
        YearMonth attendanceMonth = attendanceList.stream()
                .filter(attendance -> attendance.getAttendDate() != null)
                .map(attendance -> YearMonth.from(attendance.getAttendDate()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Attendance records must include a valid date."));
        if (attendanceList.stream()
                .filter(attendance -> attendance.getAttendDate() != null)
                .anyMatch(attendance -> !YearMonth.from(attendance.getAttendDate()).equals(attendanceMonth))) {
            throw new IllegalArgumentException("A muster sheet can only be generated from one month of attendance.");
        }
        Map<Long, List<Attendance>> attendanceByEmployee = new HashMap<>();
        for (Attendance attendance : attendanceList) {
            if (attendance.getMasterEmployeeId() != null) {
                attendanceByEmployee.computeIfAbsent(attendance.getMasterEmployeeId(), ignored -> new java.util.ArrayList<>())
                        .add(attendance);
            }
        }
        List<Employee> employees = employeeService.getAll();

        int serialNo = musterWageInfos.size() + 1;
        for (Employee employee : employees) {
            MusterWageInfo info = new MusterWageInfo();
            info.setSerialNo(serialNo++);
            info.setEmployeeName(joinName(employee.getFirstName(), employee.getLastName()));
            info.setFatherName(employee.getFatherName());
            info.setGender(employee.getGender());
            info.setDesignation(employee.getRole());
            info.setCategory(employee.getCategory());
            info.setDateOfBirth(employee.getDob());
            info.setDateOfJoining(employee.getJoiningDate());
            info.setAadharNumber(identifier(employee.getAdhaar()));
            info.setEipId(identifier(employee.getEip()));
            info.setUanNo(identifier(employee.getUan()));
            info.setEsiNo(identifier(employee.getEsic()));
            info.setWorkingHours("Day Shift");
            info.setInterval("1 Hr");
            info.setSubContractorName("Varista Industrial Solutions");

            List<Attendance> employeeAttendance = attendanceByEmployee.getOrDefault(employee.getId(), List.of());
            Map<LocalDate, Attendance> attendanceByDate = uniqueAttendanceByDate(employeeAttendance, employee);
            Map<Integer, String> dailyStatus = new HashMap<>();
            attendanceByDate.forEach((date, entry) -> dailyStatus.put(date.getDayOfMonth(), entry.getStatus()));
            info.setDailyAttendanceStatus(dailyStatus);

            calculateAttendanceTotals(attendanceByDate.values().stream().toList(), info);
            if (info.getTotalDaysWorked() > 0 && employee.getBasicSalary() == null) {
                log.warn("Employee {} has payable attendance but no basic salary configured.", employee.getId());
            }
            payrollCalculator.calculate(employee, info);
            musterWageInfos.add(info);
        }

        log.info("Muster sheet data calculation completed. Generated {} muster wage infos.", musterWageInfos.size());
    }

    private Map<LocalDate, Attendance> uniqueAttendanceByDate(List<Attendance> attendance, Employee employee) {
        Map<LocalDate, Attendance> attendanceByDate = new LinkedHashMap<>();
        for (Attendance entry : attendance) {
            if (entry.getAttendDate() == null) {
                log.warn("Skipping attendance without a date for employee {}.", employee.getId());
                continue;
            }
            Attendance existing = attendanceByDate.putIfAbsent(entry.getAttendDate(), entry);
            if (existing != null) {
                log.warn("Duplicate attendance for employee {} on {}; using the first record.",
                        employee.getId(), entry.getAttendDate());
            }
        }
        return attendanceByDate;
    }

    private void calculateAttendanceTotals(List<Attendance> attendance, MusterWageInfo info) {
        double workedDays = 0;
        double overtimeHours = 0;
        double nationalHolidayDays = 0;

        for (Attendance entry : attendance) {
            String status = entry.getStatus() == null
                    ? ""
                    : entry.getStatus().trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");

            if (ABSENCE_STATUSES.contains(status) || status.isEmpty()) {
                continue;
            }

            if (NATIONAL_HOLIDAY_STATUSES.contains(status)) {
                workedDays++;
                nationalHolidayDays++;
                continue;
            }

            if (status.equals("W/P")) {
                workedDays++;
                overtimeHours += SHIFT_HOURS;
                continue;
            }

            if (status.equals("P") || status.equals("O") || status.startsWith("OT")) {
                workedDays++;
                overtimeHours += calculateDailyOvertime(entry);
                continue;
            }

            if (status.equals("W/O")) {
                workedDays++;
                continue;
            }

            if (status.equals("AP")) {
                workedDays += partialDayCredit;
                if (partialDayCredit == 0) {
                    log.warn("Ignoring company-specific partial attendance for employee {} on {}; configure payroll.attendance.partial-day-credit to credit it.",
                            entry.getMasterEmployeeId(), entry.getAttendDate());
                }
                continue;
            }

            if (status.equals("E")) {
                workedDays += exceptionDayCredit;
                if (exceptionDayCredit == 0) {
                    log.warn("Ignoring company-specific exception attendance for employee {} on {}; configure payroll.attendance.exception-day-credit to credit it.",
                            entry.getMasterEmployeeId(), entry.getAttendDate());
                }
                continue;
            }

            log.warn("Ignoring unrecognized attendance status '{}' for employee {} on {}.",
                    entry.getStatus(), entry.getMasterEmployeeId(), entry.getAttendDate());
        }

        info.setTotalDaysWorked(workedDays);
        info.setOverTimeHours(overtimeHours);
        info.setNightHalt(nationalHolidayDays);
    }

    private double calculateDailyOvertime(Attendance entry) {
        try {
            LocalTime inTime = parseTime(entry.getInTime());
            LocalTime outTime = parseTime(entry.getOutTime());
            Duration elapsed = Duration.between(inTime, outTime);
            if (elapsed.isNegative()) {
                elapsed = elapsed.plusDays(1);
            }

            long standardShiftMinutes = Duration.ofHours(SHIFT_HOURS + BREAK_HOURS).toMinutes();
            long overtimeMinutes = Math.max(0, elapsed.toMinutes() - standardShiftMinutes);
            return overtimeMinutes / 60.0;
        } catch (DateTimeParseException | NullPointerException e) {
            log.warn("Unable to calculate overtime for employee {} on {} from times '{}' and '{}'.",
                    entry.getMasterEmployeeId(), entry.getAttendDate(), entry.getInTime(), entry.getOutTime());
            return 0;
        }
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            throw new DateTimeParseException("Time is empty", value == null ? "" : value, 0);
        }
        DateTimeParseException lastError = null;
        for (DateTimeFormatter formatter : TIME_FORMATTERS) {
            try {
                return LocalTime.parse(value.trim(), formatter);
            } catch (DateTimeParseException e) {
                lastError = e;
            }
        }
        throw lastError;
    }

    private String joinName(String firstName, String lastName) {
        return String.join(" ", firstName == null ? "" : firstName, lastName == null ? "" : lastName).trim();
    }

    private String identifier(Number identifier) {
        return identifier == null ? "" : identifier.toString();
    }
}
