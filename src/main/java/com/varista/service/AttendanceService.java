package com.varista.service;

import com.varista.entity.Attendance;
import com.varista.entity.Employee;
import com.varista.exception.InvalidAttendanceWorkbookException;
import com.varista.model.MusterWageInfo; // Changed import from MusterSheetInfo
import com.varista.model.TimesheetResponse;
import com.varista.repository.AttendanceRepository;
import com.varista.repository.EmployeeRepository;
import com.varista.utils.ExcelUtils;
import com.varista.utils.ServiceUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType; // Import CellType

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AttendanceService {
    private static final DateTimeFormatter ATTENDANCE_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter[] ATTENDANCE_TIME_INPUT_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_TIME,
            DateTimeFormatter.ofPattern("H:mm"),
            DateTimeFormatter.ofPattern("H:mm:ss"),
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH)
    };

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private ExcelUtils excelUtils;

    @Autowired
    MusterDataCalculator musterDataCalculator;

    @Transactional(rollbackFor = Exception.class)
    public ByteArrayInputStream uploadAttendance(MultipartFile file, LocalDate paymentDate) throws Exception {
        if (paymentDate == null) {
            throw new IllegalArgumentException("Payment date is required for payroll generation.");
        }
        log.info("Starting upload of attendance file: {}", file.getOriginalFilename());
        List<Attendance> attendanceList = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = openAttendanceWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();
            log.debug("Processing sheet '{}' with {} rows.", sheet.getSheetName(), lastRowNum);

            for (int i = 1; i <= lastRowNum; i++) { // Assuming first row is header
                Row row = sheet.getRow(i);
                if (row == null) {
                    log.debug("Skipping null row at index {}", i);
                    continue;
                }

                Attendance record = new Attendance();

                record.setAttendDate(convertExcelDate(row.getCell(7)));
                record.setEmployeeId(excelUtils.getCellValue(row.getCell(4)));
                record.setEmployeeName(excelUtils.getCellValue(row.getCell(5)));
                record.setInTime(normalizeAttendanceTime(row.getCell(9), "check-in", i + 1));
                record.setOutTime(normalizeAttendanceTime(row.getCell(10), "check-out", i + 1));
                record.setTotalHours(excelUtils.getCellValue(row.getCell(12)));
                record.setStatus(excelUtils.getCellValue(row.getCell(8)));
                record.setTimesheetMonth(record.getAttendDate() != null ? record.getAttendDate().getMonth().toString() : "UNKNOWN");
                record.setCompany(excelUtils.getCellValue(row.getCell(6)));

                attendanceList.add(record);
                log.debug("Parsed attendance record for employee {} on {}.", record.getEmployeeName(), record.getAttendDate());
            }
        }
        log.info("Finished parsing {} attendance records from file.", attendanceList.size());

        if (attendanceList.stream().anyMatch(attendance -> attendance.getAttendDate() == null)) {
            throw new IOException("Every attendance record must contain a valid attendance date.");
        }
        Set<YearMonth> uploadedMonths = attendanceList.stream()
                .map(attendance -> YearMonth.from(attendance.getAttendDate()))
                .collect(Collectors.toSet());
        if (uploadedMonths.size() != 1) {
            throw new IOException("Upload attendance for exactly one month at a time.");
        }
        YearMonth yearMonth = uploadedMonths.iterator().next();

        linkUserIdsToAttendance(attendanceList);
        log.info("Linked user IDs to attendance records.");

        attendanceRepository.saveAll(attendanceList);
        log.info("Saved {} attendance records to the database.", attendanceList.size());

        long employeeCount = updateUserTimesheetStatus(attendanceList);
        log.info("Updated timesheet status for {} unique employees.", employeeCount);

        // --- New Logic: Generate Muster Excel after upload and saving ---
        if (attendanceList.isEmpty()) {
            log.warn("No attendance records processed, skipping Excel generation.");
            throw new IOException("No attendance records found in the uploaded file to generate muster sheet.");
        }

        // Determine the month for the muster sheet from the first attendance record
        String month = yearMonth.getMonth().toString();

        log.info("Attempting to create muster sheet for month: {}", month);
        List<MusterWageInfo> musterWageInfos = new ArrayList<>();

        musterDataCalculator.calculateMusterSheetData(attendanceList, musterWageInfos, paymentDate);
        log.info("Muster data calculated. Generated {} MusterWageInfo entries.", musterWageInfos.size());

        if (!musterWageInfos.isEmpty()) {
            var s = musterWageInfos.get(0);
            log.debug("Sample muster info: serialNo={}, employeeName={}, dailyAttendanceStatus size={}", s.getSerialNo(), s.getEmployeeName(), (s.getDailyAttendanceStatus() == null ? 0 : s.getDailyAttendanceStatus().size()));
        }

        ByteArrayInputStream inputStream = excelUtils.generateMasterRegisterFile(musterWageInfos, yearMonth);
        log.info("Muster Excel sheet generated successfully for month: {}.", month);
        return inputStream;
    }

    private Workbook openAttendanceWorkbook(InputStream inputStream) throws IOException {
        try {
            return WorkbookFactory.create(inputStream);
        } catch (IllegalArgumentException | IOException e) {
            throw new InvalidAttendanceWorkbookException(
                    "The uploaded Excel file is invalid or damaged. Open it in Excel, save it as a new .xlsx file, and upload it again.",
                    e);
        }
    }

    private long updateUserTimesheetStatus(List<Attendance> attendanceList) {
        log.info("Updating timesheet status for employees based on uploaded attendance.");
        List<Long> masterIds = attendanceList.stream()
                .map(Attendance::getMasterEmployeeId)
                .filter(id -> id != null && !id.toString().trim().isEmpty())
                .map(Long::valueOf)
                .distinct()
                .collect(Collectors.toList());

        if (!masterIds.isEmpty()) {
            log.debug("Found {} unique master employee IDs to update.", masterIds.size());
            List<Employee> employees = employeeRepository.findAllById(masterIds);

            employees.forEach(employee -> {
                employee.setTimesheetPresent(true);
                log.debug("Set timesheetPresent to true for employee ID: {}", employee.getId());
            });

            employeeRepository.saveAll(employees);
            log.info("Successfully updated timesheet status for {} employees.", employees.size());
            return employees.size();
        } else {
            log.warn("No valid MasterEmployeeIds found in the attendance list to update timesheet status.");
        }
        return 0;
    }

    public void linkUserIdsToAttendance(List<Attendance> attendanceList) {
        log.info("Linking user IDs to attendance records.");
        Set<List<String>> uniqueNames = new HashSet<>();
        for (Attendance a : attendanceList) {
            String[] parts = ServiceUtils.splitName(a.getEmployeeName());
            uniqueNames.add(Arrays.asList(parts[0], parts[1]));
        }
        log.debug("Extracted {} unique employee names from attendance list.", uniqueNames.size());

        List<String> nameKeys = uniqueNames.stream()
                .map(list -> (list.get(0) + "|" + list.get(1)).toLowerCase())
                .collect(Collectors.toList());

        List<Employee> foundEmployee = employeeRepository.findBySpecificNameKeys(nameKeys);
        log.debug("Found {} employees in the database matching unique names.", foundEmployee.size());

        Map<String, Long> nameToIdMap = foundEmployee.stream()
                .collect(Collectors.toMap(
                        u -> (u.getFirstName() + "|" + u.getLastName()).toLowerCase(),
                        Employee::getId,
                        (id1, id2) -> id1 // In case of duplicate names, pick the first ID
                ));

        attendanceList.forEach(attendance -> {
            String[] parts = ServiceUtils.splitName(attendance.getEmployeeName());
            String lookupKey = (parts[0] + "|" + parts[1]).toLowerCase();

            Long userId = nameToIdMap.get(lookupKey);
            if (userId != null) {
                attendance.setMasterEmployeeId(userId);
                log.debug("Linked attendance for '{}' to MasterEmployeeId: {}", attendance.getEmployeeName(), userId);
            } else {
                log.warn("No user found in database for name: '{}'. Attendance record will not have a MasterEmployeeId.", attendance.getEmployeeName());
            }
        });
        log.info("Finished linking user IDs to attendance records.");
    }

    private LocalDate convertExcelDate(Cell cell) {
        log.debug("Attempting to convert Excel cell to LocalDate.");
        if (cell == null) {
            log.debug("Excel cell is null, returning null date.");
            return null;
        }

        try {
            // Try to get date as a string first, assuming "dd/MM/yyyy" format
            String dateString = excelUtils.getCellValue(cell); // Use existing utility to get string value
            if (dateString != null && !dateString.trim().isEmpty()) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                LocalDate date = LocalDate.parse(dateString, formatter);
                log.debug("Successfully parsed date '{}' from string format.", dateString);
                return date;
            }
        } catch (Exception e) {
            log.warn("Could not parse date from string format for cell. Attempting numeric conversion. Error: {}", e.getMessage());
            // Fallback to numeric date if string parsing fails
        }

        try {
            // If string parsing failed or was empty, try numeric (Excel date)
            if (cell.getCellType() == CellType.NUMERIC && org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                LocalDate date = org.apache.poi.ss.usermodel.DateUtil
                        .getLocalDateTime(cell.getNumericCellValue())
                        .toLocalDate();
                log.debug("Successfully parsed date '{}' from numeric Excel format.", date);
                return date;
            }
        } catch (Exception e) {
            log.error("Failed to convert Excel cell to date (numeric or string). Cell value: {}. Error: {}", excelUtils.getCellValue(cell), e.getMessage());
        }
        return null;
    }

    private String normalizeAttendanceTime(Cell cell, String fieldName, int rowNumber) throws InvalidAttendanceWorkbookException {
        String value = excelUtils.getCellValue(cell);
        if (value.isBlank()) return "";

        for (DateTimeFormatter formatter : ATTENDANCE_TIME_INPUT_FORMATS) {
            try {
                return LocalTime.parse(value, formatter).format(ATTENDANCE_TIME_FORMAT);
            } catch (DateTimeParseException ignored) {
                // Try the supported workbook time formats.
            }
        }

        if (cell != null && cell.getCellType() == CellType.NUMERIC
                && org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
            return org.apache.poi.ss.usermodel.DateUtil.getLocalDateTime(cell.getNumericCellValue())
                    .toLocalTime()
                    .format(ATTENDANCE_TIME_FORMAT);
        }

        DateTimeParseException cause = new DateTimeParseException("Unsupported time format", value, 0);
        throw new InvalidAttendanceWorkbookException(
                "Invalid " + fieldName + " time at spreadsheet row " + rowNumber + ": " + value,
                cause);
    }

    public TimesheetResponse getEmployeeTimesheet(long employeeId) {
        log.info("Fetching timesheet for employee ID: {}", employeeId);
        TimesheetResponse response = new TimesheetResponse();

        List<Attendance> timesheetList = attendanceRepository.findByMasterEmployeeId(employeeId);
        log.debug("Retrieved {} attendance records for employee ID: {}.", timesheetList.size(), employeeId);

        long leavesCount = timesheetList.stream()
                .filter(attendance -> "AB".equalsIgnoreCase(attendance.getStatus()))
                .count();
        long workDaysCount = timesheetList.stream()
                .filter(attendance -> "P".equalsIgnoreCase(attendance.getStatus()))
                .count();
        double totalWorkingHours = timesheetList.stream()
                .map(Attendance::getTotalHours)
                .filter(hours -> hours != null && hours.contains(":"))
                .mapToDouble(this::parseWorkingHours)
                .sum();

        response.setEmployeeId(employeeId);
        response.setLeavesCount(leavesCount);
        response.setWorkdaysCount(workDaysCount);
        response.setWorkingHrs(totalWorkingHours);
        response.setMonthlyTimesheet(timesheetList);
        response.setWeeklyOffs(getWeeklyOffDays(timesheetList));
        log.info("Generated timesheet response for employee ID: {}. Leaves: {}, Workdays: {}, Total Hours: {}",
                employeeId, leavesCount, workDaysCount, totalWorkingHours);
        return response;
    }

    private double parseWorkingHours(String timeString) {
        String[] parts = timeString.trim().split(":");
        if (parts.length < 2 || parts.length > 3) {
            log.warn("Ignoring malformed attendance duration '{}'.", timeString);
            return 0;
        }
        try {
            long hours = Long.parseLong(parts[0]);
            long minutes = Long.parseLong(parts[1]);
            long seconds = parts.length == 3 ? Long.parseLong(parts[2]) : 0;
            if (hours < 0 || minutes < 0 || minutes >= 60 || seconds < 0 || seconds >= 60) {
                log.warn("Ignoring out-of-range attendance duration '{}'.", timeString);
                return 0;
            }
            return hours + (minutes / 60.0) + (seconds / 3600.0);
        } catch (NumberFormatException e) {
            log.warn("Ignoring malformed attendance duration '{}'.", timeString);
            return 0;
        }
    }

    private String getWeeklyOffDays(List<Attendance> timesheetList) {
        log.debug("Calculating weekly off days from timesheet list.");
        String weeklyOffs = timesheetList.stream()
                .filter(attendance -> attendance.getStatus() != null
                        && "W/O".equalsIgnoreCase(attendance.getStatus().trim().replaceAll("\\s+", "")))
                .filter(attendance -> attendance.getAttendDate() != null)
                .map(attendance -> attendance.getAttendDate()
                        .getDayOfWeek()
                        .getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
                .distinct()
                .collect(Collectors.joining(", "));
        log.debug("Calculated weekly off days: {}", weeklyOffs);
        return weeklyOffs;
    }

    // Removed the createAttendanceSheet method as its logic is now integrated into uploadAttendance
    // public ByteArrayInputStream createAttendanceSheet(String month) throws IOException { ... }

    public Map<String, Object> inspectTemplateHeaders() throws IOException {
        log.info("Inspecting Excel template headers.");
        Map<String, Object> headers = excelUtils.inspectTemplateHeaders();
        log.info("Successfully inspected template headers: {}", headers.keySet());
        return headers;
    }

}