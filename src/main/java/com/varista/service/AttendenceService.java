package com.varista.service;

import com.varista.entity.Attendance;
import com.varista.entity.Employee;
import com.varista.model.TimesheetResponse;
import com.varista.repository.AttendanceRepository;
import com.varista.repository.EmployeeRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AttendenceService {

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Transactional(rollbackFor = Exception.class)
    public long uploadAttendence(MultipartFile file) throws Exception {
        List<Attendance> attendanceList = new ArrayList<>();

        // 1. Use WorkbookFactory to support both .xls (OLE2) and .xlsx (OOXML)
        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Attendance record = new Attendance();

                // Fixed column mapping based on your previous descriptions
                // Column 0: Date, 1: Employee ID, 2: Name, 3: In, 4: Out, 5: Hours, 6: Status
                record.setAttendDate(convertExcelDate(row.getCell(7)));
                record.setEmployeeId(getCellValue(row.getCell(4)));
                record.setEmployeeName(getCellValue(row.getCell(5)));
                record.setInTime(getCellValue(row.getCell(9)));
                record.setOutTime(getCellValue(row.getCell(10)));
                record.setTotalHours(getCellValue(row.getCell(12)));
                record.setStatus(getCellValue(row.getCell(8)));

                // Set default company or extra fields if needed
                record.setCompany(getCellValue(row.getCell(6)));

                attendanceList.add(record);
            }
        }

        // 2. Link User IDs to the Attendance records BEFORE saving
        linkUserIdsToAttendance(attendanceList);

        // 3. Save Attendance Records
        var result = attendanceRepository.saveAll(attendanceList);

        // 4. Update the User table (TimesheetPresent flag)
        long employeeCount = updateUserTimesheetStatus(attendanceList);

        return employeeCount;
    }

    private long updateUserTimesheetStatus(List<Attendance> attendanceList) {
        // 1. Extract all MasterEmployeeIds from the list, filter nulls/empties, and convert to Long
        List<Long> masterIds = attendanceList.stream()
                .map(Attendance::getMasterEmployeeId)
                .filter(id -> id != null && !id.toString().trim().isEmpty())
                .map(Long::valueOf)
                .distinct() // Ensure we don't fetch the same user multiple times
                .collect(Collectors.toList());

        if (!masterIds.isEmpty()) {
            // 2. Fetch all users in a single batch call using the Primary Keys
            // findAllById is highly optimized and much faster than name-based queries
            List<Employee> employees = employeeRepository.findAllById(masterIds);

            // 3. Update the required status field
            employees.forEach(employee -> {
                employee.setTimesheetPresent(true);
                // If you specifically meant attendanceStatus as well:
                // user.setAttendanceStatus("Present");
            });

            // 4. Save all updated users back to the database in one transaction
            employeeRepository.saveAll(employees);

            log.info("Successfully updated timesheet status for {} users.", employees.size());
            return employees.size();
        } else {
            log.warn("No valid MasterEmployeeIds found in the attendance list to update.");
        }
        return 0;
    }

    public void linkUserIdsToAttendance(List<Attendance> attendanceList) {
        Set<List<String>> uniqueNames = new HashSet<>();
        for (Attendance a : attendanceList) {
            String[] parts = splitName(a.getEmployeeName());
            uniqueNames.add(Arrays.asList(parts[0], parts[1]));
        }

        // Inside uploadAttendence or linkUserIdsToAttendance
        List<String> nameKeys = uniqueNames.stream()
                .map(list -> (list.get(0) + "|" + list.get(1)).toLowerCase())
                .collect(Collectors.toList());

// Single DB call using string keys
        List<Employee> foundEmployee = employeeRepository.findBySpecificNameKeys(nameKeys);

        Map<String, Long> nameToIdMap = foundEmployee.stream()
                .collect(Collectors.toMap(
                        u -> (u.getFirstName() + "|" + u.getLastName()).toLowerCase(),
                        Employee::getId,
                        (id1, id2) -> id1
                ));

        attendanceList.forEach(attendance -> {
            String[] parts = splitName(attendance.getEmployeeName());
            String lookupKey = (parts[0] + "|" + parts[1]).toLowerCase();

            Long userId = nameToIdMap.get(lookupKey);
            if (userId != null) {
                attendance.setMasterEmployeeId(userId);
            } else {
                log.warn("No user found in database for name: {}", attendance.getEmployeeName());
            }
        });
    }

    // Helper to handle the "First word = First Name, Rest = Last Name" logic
    private String[] splitName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return new String[]{"", ""};
        String[] nameArr = fullName.trim().split(" ", 2);
        String first = nameArr[0];
        String last = (nameArr.length > 1) ? nameArr[1] : "";
        return new String[]{first, last};
    }

    // Helper to handle Excel Cell types (String vs Numeric/Date)
    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((int) cell.getNumericCellValue());
            default -> "";
        };
    }

    // Helper to handle Excel Date formats
    private LocalDate convertExcelDate(Cell cell) {
        if (cell == null) return null;

        // Fallback if date is stored as a string
        try {
            String date = cell.getStringCellValue();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            return LocalDate.parse(date, formatter);
        } catch (Exception e) {
            return null;
        }
    }

    public TimesheetResponse getEmployeeTimesheet(long employeeId) {

        TimesheetResponse response = new TimesheetResponse();

        List<Attendance> timesheetList = attendanceRepository.findByMasterEmployeeId(employeeId);

// Filter for status matching "AB" (case-sensitive) and count them
        long leavesCount = timesheetList.stream()
                .filter(attendance -> "AB".equalsIgnoreCase(attendance.getStatus()))
                .count();
        long workDaysCount = timesheetList.stream()
                .filter(attendance -> "P".equalsIgnoreCase(attendance.getStatus()))
                .count();
        double totalWorkingHours = timesheetList.stream()
                .map(Attendance::getTotalHours)
                .filter(hours -> hours != null && hours.contains(":"))
                .mapToDouble(timeStr -> {
                    String[] parts = timeStr.split(":");
                    double hours = Double.parseDouble(parts[0]);
                    double minutes = Double.parseDouble(parts[1]);
                    return hours + (minutes / 60.0);
                })
                .sum();

        response.setEmployeeId(employeeId);
        response.setLeavesCount(leavesCount);
        response.setWorkdaysCount(workDaysCount);
        response.setWorkingHrs(totalWorkingHours);
        response.setMonthlyTimesheet(timesheetList);
        response.setWeeklyOffs(getWeeklyOffDays(timesheetList));
        return response;
    }

    private String getWeeklyOffDays(List<Attendance> timesheetList) {


        return timesheetList.stream()
                // 1. Filter for status matching "O" (Weekly Off)
                .filter(attendance -> "O".equalsIgnoreCase(attendance.getStatus()))
                // 2. Ensure date is not null
                .filter(attendance -> attendance.getAttendDate() != null)
                // 3. Map to Day of the Week Name
                .map(attendance -> {
                    // Convert java.sql.Date to java.time.LocalDate
                    return attendance.getAttendDate()
                            .getDayOfWeek()
                            .getDisplayName(TextStyle.SHORT, Locale.ENGLISH); // Returns Mon, Tue, etc.
                })
                // 4. Collect distinct days (in case the list covers multiple weeks)
                .distinct()
                // 5. Join into a single string
                .collect(Collectors.joining(", "));

    }
}