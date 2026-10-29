# Timesheet Muster Master Sheet Generation Logic

This document outlines the logic used to process uploaded timesheet Excel files and generate a Muster Master Sheet and Master Wage Sheet, including calculations for total working days, overtime (OT) hours, and national/public holiday (NH/PH) hours.

## 1. Overall Flow

The process has been streamlined into a single operation:

1.  **Timesheet Upload & Generation**: An Excel file containing daily attendance records is uploaded via the `/timesheet/upload` API endpoint.
2.  **Data Processing**: The `AttendenceService` parses the Excel data, links attendance records to existing employees, and saves them to the database.
3.  **Muster Data Calculation**: Immediately after processing and saving attendance, the `AttendenceService` retrieves relevant attendance records and passes them to `MusterDataCalculator` to aggregate and calculate muster-specific data per employee, populating `MusterWageInfo` objects.
4.  **Excel Generation**: The `ExcelUtils` component then takes these populated `MusterWageInfo` objects and generates a single Excel file containing both the "Muster Master Sheet" and "Master Wage Sheet".
5.  **File Download**: The generated Excel file is returned directly as a downloadable response from the `/timesheet/upload` endpoint.

## 2. Key Components Involved

*   **`AttendenceService`**: Handles the initial Excel upload, parsing, linking attendance to employees, saving records, orchestrating the muster data calculation, and triggering the final Excel generation. It now returns the generated Excel file.
*   **`MusterDataCalculator`**: Contains the core business logic for calculating various attendance metrics (total days, OT hours, NH hours) for each employee based on their daily attendance records, populating `MusterWageInfo` objects.
*   **`ExcelUtils`**: Provides utility methods for reading Excel files (e.g., `getCellValue`, `inspectTemplateHeaders`) and generating new Excel files (e.g., `generateAttendanceSheet`), now handling both Muster Master and Master Wage sheets using `MusterWageInfo`.
*   **`EmployeeService`**: Used by `MusterDataCalculator` to fetch employee details (like name, DOB, designation, joining date, etc.) for the muster sheet.
*   **`AttendanceRepository`**: JPA repository for `Attendance` entities.
*   **`EmployeeRepository`**: JPA repository for `Employee` entities.
*   **`MusterWageInfo`**: A comprehensive model class that now holds all the necessary data for both the Muster Master and Master Wage sheets.

## 3. Detailed Logic

### 3.1. Timesheet Upload & Excel Generation (`AttendenceService.uploadAttendence`)

This method is now the central point for both timesheet ingestion and muster sheet generation.

1.  **File Reading**:
    *   Accepts a `MultipartFile` (the uploaded Excel file).
    *   Uses `WorkbookFactory.create(is)` to open the Excel workbook, supporting both `.xls` and `.xlsx` formats.
    *   Iterates through rows, starting from the second row (assuming the first row is headers).
2.  **Data Extraction**:
    *   For each row, it extracts data from specific columns and maps them to an `Attendance` entity.
    *   **Column Mapping (Example based on your code)**:
        *   `AttendDate`: `row.getCell(7)` (converted using `convertExcelDate`)
        *   `EmployeeId`: `excelUtils.getCellValue(row.getCell(4))`
        *   `EmployeeName`: `excelUtils.getCellValue(row.getCell(5))`
        *   `InTime`: `excelUtils.getCellValue(row.getCell(9))`
        *   `OutTime`: `excelUtils.getCellValue(row.getCell(10))`
        *   `TotalHours`: `excelUtils.getCellValue(row.getCell(12))`
        *   `Status`: `excelUtils.getCellValue(row.getCell(8))`
        *   `Company`: `excelUtils.getCellValue(row.getCell(6))`
    *   `TimesheetMonth` is derived from `AttendDate`.
3.  **Date Conversion (`AttendenceService.convertExcelDate`)**:
    *   Attempts to parse the cell value as a `LocalDate`.
    *   First tries to parse as a `String` in "dd/MM/yyyy" format.
    *   If string parsing fails, it attempts to convert from Excel's numeric date format (`CellType.NUMERIC` and `DateUtil.isCellDateFormatted`).
4.  **Employee Linking (`AttendenceService.linkUserIdsToAttendance`)**:
    *   Extracts unique employee names (first name, last name) from the parsed `Attendance` records.
    *   Queries the `EmployeeRepository` (`findBySpecificNameKeys`) to find matching `Employee` records in the database.
    *   Creates a map of employee name (e.g., "firstname|lastname") to their `masterEmployeeId` (database ID).
    *   Iterates through the `attendanceList` and sets the `masterEmployeeId` for each `Attendance` record if a match is found. Logs warnings for unlinked employees.
5.  **Saving Records**:
    *   `attendanceRepository.saveAll(attendanceList)`: Persists all processed `Attendance` records to the database in a single batch.
6.  **Muster Data Calculation**:
    *   Determines the `YearMonth` from the processed attendance data.
    *   Initializes a `List<MusterWageInfo>`.
    *   Calls `musterDataCalculator.calculateMusterSheetData(attendanceList, musterWageInfos)` to populate the `MusterWageInfo` objects.
7.  **Excel Generation**:
    *   `excelUtils.generateAttendanceSheet(musterWageInfos, yearMonth)`: Uses the populated `MusterWageInfo` list and the `YearMonth` to generate the final Excel file containing both Muster Master and Master Wage sheets.
8.  **Return Excel**: The generated `ByteArrayInputStream` (the Excel file) is returned to the controller for download.

### 3.2. Muster Data Calculation (`MusterDataCalculator.calculateMusterSheetData`)

This method now populates the comprehensive `MusterWageInfo` objects.

1.  **Fetch All Employees**:
    *   `employeeService.getAll()`: Retrieves all registered employees to ensure all relevant employees are considered.
2.  **Iterate and Populate `MusterWageInfo`**:
    *   For each `Employee`:
        *   Creates a `MusterWageInfo` object.
        *   **Basic Information**: Populates `serialNo`, `employeeName`, `fatherName`, `gender`, `designation`, `category` from the `Employee` entity.
        *   **Dates**: Populates `dateOfBirth`, `dateOfJoining` from `Employee`.
        *   **Statutory Identifiers**: Populates `aadharNumber`, `eipId`, `uanNo`, `esiNo` from `Employee`.
        *   **Attendance/Muster Specifics**:
            *   `dailyAttendanceStatus`: A `Map<Integer, String>` where the key is the day of the month (1-31) and the value is the `status` (P, AB, O, NH, PH, OT, etc.) for that day. This is populated by filtering the `attendanceList` for the current employee.
            *   `workingHours`, `interval`: Set to default values (e.g., "Day Shift", "1 Hr").
        *   **Financial/Wage Details**: These fields (`dailyWage`, `basicWages`, `otAmount`, `nhAmount`, `hra`, `grossWage`, `pfDeduction`, `esicDeduction`, `ptDeduction`, `lwfDeduction`, `otherDeductions`, `totalDeductions`, `netWage`, `dateOfPayment`) are initialized to `0.0`, `null`, or default values as their calculation logic is not present in this context.
        *   **Project Metadata**: `jobCode`, `subContractorName`, `remarks` are initialized to `null` or default values. `subContractorName` is set to "Varista Industrial Solutions".
        *   Calls `calculateTotalOTAndHNDays` to compute aggregated metrics (`totalDaysWorked`, `overTimeHours`, `nightHalt`).
        *   Adds the populated `MusterWageInfo` to the `musterWageInfos` list.
3.  **Re-index `serialNo`**: Ensures the serial numbers are sequential after all employees have been processed.

### 3.3. Total Days, OT, and NH Hours Calculation (`MusterDataCalculator.calculateTotalOTAndHNDays`)

This method aggregates the daily attendance statuses and times into total metrics for each employee, storing them in `MusterWageInfo`.

1.  **Initialization**: `totalDaysWorked = 0`, `overTimeHours = 0`, `nightHalt = 0`.
2.  **Iterate Daily Entries**: Loops through each `Attendance` record for a specific employee.
3.  **Calculate Total Days Worked**:
    *   If `status` matches `P` (Present), `AP` (Absent with Pay), `NH` (National Holiday), `PH` (Public Holiday), or `OT` (Overtime), `totalDaysWorked` is incremented. This defines what counts as a "working day" for muster purposes.
4.  **Calculate Overtime Hours**:
    *   If `status` contains `"OT"` or is `"AP"` or `"P"`, it calls `calculateDailyOT(dayEntry)` to get the overtime for that specific day and adds it to `overTimeHours`.
5.  **Calculate Night Halt (NH) Hours**:
    *   If `status` is `"NH"` or `"PH"`, `nightHalt` is incremented by `1.0`. This typically represents a day's worth of holiday pay.
6.  **Set Results**: The calculated `totalDaysWorked`, `overTimeHours`, and `nightHalt` are set on the `MusterWageInfo` object.

### 3.4. Daily Overtime Calculation (`MusterDataCalculator.calculateDailyOT`)

This method determines the overtime hours for a single day's attendance record.

1.  **Time Parsing**:
    *   Parses `InTime` and `OutTime` from the `Attendance` entry into `LocalTime` objects.
2.  **Effective In-Time Adjustment**:
    *   `effectiveInTime` is set to `9:30 AM` if the `actualInTime` is earlier than `9:30 AM`. Otherwise, it uses the `actualInTime`. This implies a policy where work before 9:30 AM is not counted towards regular or overtime hours.
3.  **Duration Calculation**:
    *   Calculates the `duration` between `effectiveInTime` and `outTime` in hours.
4.  **Lunch Break Deduction**:
    *   Subtracts `1.0` hour from the `duration` (assuming a fixed 1-hour lunch break).
5.  **Overtime Threshold**:
    *   Applies a threshold: if the calculated `otHours` are less than `1.0` hour, the daily overtime is considered `0`. Otherwise, the calculated `otHours` are used. This means only significant overtime (1 hour or more) is counted.
6.  **Error Handling**: Includes `try-catch` blocks for `DateTimeParseException` and general `Exception` to handle invalid time formats gracefully, returning `0` OT hours in such cases.

## 4. Excel Template (`timesheet_template.xlsx`) and `ExcelUtils.generateAttendanceSheet`

The `ExcelUtils.generateAttendanceSheet` method is now responsible for populating a pre-existing Excel template (`timesheet_template.xlsx`) with data from `MusterWageInfo` into two distinct sheets:

1.  **"Muster Master Sheet" (Sheet 1)**:
    *   Populated using `fillMusterMasterData`.
    *   Maps `MusterWageInfo` fields like `serialNo`, `aadharNumber`, `eipId`, `employeeName`, `designation`, `dateOfBirth`, `dateOfJoining`, `workingHours`, `interval`, `dailyAttendanceStatus` (for daily cells), `totalDaysWorked`, `overTimeHours`, `nightHalt`, `jobCode`, `subContractorName`.
    *   Applies styling for data and highlights Sundays.

2.  **"Master Wage Sheet" (Sheet 2)**:
    *   Populated using `fillMusterWageData`.
    *   Maps `MusterWageInfo` fields like `serialNo`, `employeeName`, `designation`, `totalDaysWorked`, `overTimeHours`, `nightHalt`, `dailyWage`, `basicWages`, `otAmount`, `nhAmount`, `hra`, `grossWage`, `pfDeduction`, `esicDeduction`, `ptDeduction`, `lwfDeduction`, `otherDeductions`, `totalDeductions`, `netWage`, `dateOfPayment`, `jobCode`, `subContractorName`, `remarks`.
    *   Includes dynamic headers and applies styling.

---
This document provides a comprehensive overview of the timesheet muster master sheet generation logic within your application, reflecting the latest changes.
