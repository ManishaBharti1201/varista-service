package com.varista.service;

import com.varista.entity.Attendance;
import com.varista.entity.Employee;
import com.varista.model.MusterSheetInfo;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.YearMonth;
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

    @Autowired
    private ExcelUtils excelUtils;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    MusterDataCalculator musterDataCalculator;

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
                record.setEmployeeId(excelUtils.getCellValue(row.getCell(4)));
                record.setEmployeeName(excelUtils.getCellValue(row.getCell(5)));
                record.setInTime(excelUtils.getCellValue(row.getCell(9)));
                record.setOutTime(excelUtils.getCellValue(row.getCell(10)));
                record.setTotalHours(excelUtils.getCellValue(row.getCell(12)));
                record.setStatus(excelUtils.getCellValue(row.getCell(8)));
                record.setTimesheetMonth(record.getAttendDate().getMonth().toString());

                // Set default company or extra fields if needed
                record.setCompany(excelUtils.getCellValue(row.getCell(6)));

                attendanceList.add(record);
            }
        }
        // 2. Link User IDs to the Attendance records BEFORE saving
        linkUserIdsToAttendance(attendanceList);
        // 3. Save Attendance Records
        attendanceRepository.saveAll(attendanceList);
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
            String[] parts = ServiceUtils.splitName(a.getEmployeeName());
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
            String[] parts = ServiceUtils.splitName(attendance.getEmployeeName());
            String lookupKey = (parts[0] + "|" + parts[1]).toLowerCase();

            Long userId = nameToIdMap.get(lookupKey);
            if (userId != null) {
                attendance.setMasterEmployeeId(userId);
            } else {
                log.warn("No user found in database for name: {}", attendance.getEmployeeName());
            }
        });
    }

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

    public ByteArrayInputStream createAttendanceSheet(String month) throws IOException {
        List<MusterSheetInfo> musterSheetInfos = new ArrayList<>();

        try {
            List<Attendance> attendanceList = attendanceRepository.findByMonth(month);

            musterDataCalculator.calculateMusterSheetData(attendanceList, musterSheetInfos);

            log.info("createAttendanceSheet: calculated musterSheetInfos size={}", musterSheetInfos.size());
            if (!musterSheetInfos.isEmpty()) {
                var s = musterSheetInfos.get(0);
                log.info("Sample muster info: slNo={}, name={}, calSize={}", s.getSlNo(), s.getEmployeeName(), (s.getCalender() == null ? 0 : s.getCalender().size()));
            }

            YearMonth yearMonth = YearMonth.from(attendanceList.get(0).getAttendDate());

            return excelUtils.generateAttendanceSheet(musterSheetInfos, yearMonth);
        } catch (Exception e) {
            log.error("Failed to create attendance sheet: {}", e.getMessage(), e);
            throw new IOException("Failed to create attendance sheet", e);
        }
    }

    public Map<String, Object> inspectTemplateHeaders() throws IOException {
        return excelUtils.inspectTemplateHeaders();
    }

}
