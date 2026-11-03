# Timesheet Muster Master Sheet Generation Logic

This document outlines the logic used to process uploaded timesheet Excel files and generate a Muster Master Sheet and Master Wage Sheet, including calculations for total working days, overtime (OT) hours, and national/public holiday (NH/PH) hours.

## 1. Overall Flow

The process has been streamlined into a single operation:

1.  **Timesheet Upload & Generation**: An Excel file containing daily attendance records is uploaded via the `/timesheet/upload` API endpoint.
2.  **Data Processing**: The `AttendenceService` parses the Excel data, links attendance records to existing employees, and saves them to the database.
3.  **Muster Data Calculation**: Immediately after processing and saving attendance, the `AttendenceService` retrieves relevant attendance records and passes them to `MusterDataCalculator` to aggregate and calculate muster-specific data per employee, populating `MusterWageInfo` objects.
4.  **Excel Generation**: `ExcelUtils.generateMasterRegisterFile` uses the formatted salary-register template to generate a workbook containing a monthly muster sheet and wage register.
5.  **File Download**: The generated Excel file is returned directly as a downloadable response from the `/timesheet/upload` endpoint.

## 2. Key Components Involved

*   **`AttendenceService`**: Handles the initial Excel upload, parsing, linking attendance to employees, saving records, orchestrating the muster data calculation, and triggering the final Excel generation. It now returns the generated Excel file.
*   **`MusterDataCalculator`**: Contains the core business logic for calculating various attendance metrics (total days, OT hours, NH hours) for each employee based on their daily attendance records, populating `MusterWageInfo` objects.
*   **`ExcelUtils`**: Provides utility methods for reading Excel files (e.g., `getCellValue`, `inspectTemplateHeaders`) and generates both register sheets from `salary_register_template.xlsx` using `MusterWageInfo`.
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
    *   Calls `musterDataCalculator.calculateMusterSheetData(attendanceList, musterWageInfos, paymentDate)` to populate the `MusterWageInfo` objects for the payment date supplied with this upload.
7.  **Excel Generation**:
    *   The upload supplies a payment date for this payroll run. `excelUtils.generateMasterRegisterFile(musterWageInfos, yearMonth)` uses the populated `MusterWageInfo` list and the `YearMonth` to generate the final Excel file containing both Muster Master and Master Wage sheets.
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
        *   **Financial/Wage Details**: `PayrollCalculator` selects the effective-dated category minimum wage, calculates HRA at 5% and interim bonus at 8.33%, and prorates employee-specific washing/special/other allowances and deductions. PF uses 12% of earned basic, washing, and special allowance, capped at ₹15,000. Employee and employer ESIC use 0.75% and 3.25% of gross less earned washing allowance for employees with an ESIC identifier. Professional tax uses deployment-configured slabs. The payment date is supplied for each payroll run.
        *   **Project Metadata**: `jobCode`, `subContractorName`, `remarks` are initialized to `null` or default values. `subContractorName` is set to "Varista Industrial Solutions".
        *   Calls `calculateTotalOTAndHNDays` to compute aggregated metrics (`totalDaysWorked`, `overTimeHours`, `nightHalt`).
        *   Adds the populated `MusterWageInfo` to the `musterWageInfos` list.
3.  **Re-index `serialNo`**: Ensures the serial numbers are sequential after all employees have been processed.

### 3.3. Total Days, OT, and NH Hours Calculation (`MusterDataCalculator.calculateTotalOTAndHNDays`)

This method aggregates the daily attendance statuses and times into total metrics for each employee, storing them in `MusterWageInfo`.

1.  **Initialization**: `totalDaysWorked = 0`, `overTimeHours = 0`, `nightHalt = 0`.
2.  **Iterate Daily Entries**: Loops through each `Attendance` record for a specific employee.
3.  **Calculate Paid Days**:
    *   `P`, `O`, and `OT` each add one paid day and calculate ordinary OT from their recorded times.
    *   `NH`, `N/H`, and `PH` each add one paid day and one holiday day.
    *   `W/O` is a weekly-off marker and does not add a prorated paid day. `W/P` records work on a weekly off and contributes eight rest-OT hours without adding a prorated paid day.
    *   `A` and `AB` add no paid days. `AP` and `E` use the configured `payroll.attendance.partial-day-credit` and `payroll.attendance.exception-day-credit` values.
4.  **Set Results**: The calculated paid days, ordinary plus rest overtime hours, and holiday days are set on the `MusterWageInfo` object.

### 3.4. Daily Overtime Calculation (`MusterDataCalculator.calculateDailyOT`)

This method determines the overtime hours for a single day's attendance record.

1.  Parses `InTime` and `OutTime` into `LocalTime` values, including overnight shifts.
2.  Calculates ordinary overtime as `max(elapsed hours - 9, 0)`, where nine hours represents an eight-hour shift plus a one-hour unpaid break.
3.  Invalid or missing time values are logged and contribute zero ordinary OT. Weekly-off work is accounted for separately as eight rest-OT hours per `W/P` day.

## 4. Salary Register Template and Output Mapping

`ExcelUtils.generateMasterRegisterFile` loads `salary_register_template.xlsx`, a sanitized copy of the supplied salary-register example. The template retains its headers, cell styles, column widths, merged cells, row heights, and print settings; employee/payroll values, formulas, comments, and identifying document metadata are removed. Generation updates the month and contractor/project titles without changing the reference formatting. It does not auto-size columns or apply replacement styles across the workbook.

1. **Muster sheet**: Employee serial number, EIP identifier, and name appear before one column per calendar day. Present days (`P`, `O`) and national/public holidays are included in paid days. `W/O` does not add another prorated day; `W/P` adds eight rest-OT hours. Ordinary OT and rest OT are added to total OT. Sundays use the template’s Sunday cell style. The layout adjusts summary columns for 28–31 day months.
2. **Wage register**: Minimum wages are selected by category from `payroll.minimum-wages.schedule`, using the latest effective schedule not later than the payroll month. The May 2026 register indicates Unskilled = ₹15,306, Semiskilled = ₹16,316, and Skilled = ₹17,736; the August register indicates ₹15,585, ₹16,595, and ₹18,015 respectively. Update the schedule when a new approved rate takes effect. Category matching ignores case, spaces, and hyphens; unknown categories and months before the first schedule fail payroll generation. HRA is 5% and interim bonus is 8.33% of fixed minimum wages. Washing, special, and other allowances and deductions use the employee's stored payroll profile; updates from the employee form preserve those stored fields when omitted. Fixed gross salary is the sum of fixed minimum wages and fixed allowances. Earned wage components are prorated using the existing 26-day divisor. PF is 12% of earned basic plus washing and special allowance, capped at ₹15,000, following the selected August-register basis. Employee and employer ESIC are 0.75% and 3.25% of gross less earned washing allowance when an ESIC identifier is present. Professional tax uses threshold entries in `payroll.professional-tax.slabs` (the observed 2026 register values are ₹0 through ₹7,500, ₹175 above ₹7,500 through ₹10,000, and ₹200 above ₹10,000). Supply the payment date on each upload; the register does not infer a fixed day from prior examples. Zero allowances/deductions remain zero if their employee profile values are absent. These inferred rules should be checked against current statutory guidance and approved payroll policy.
3. **Totals and values**: Numeric wage columns are totaled in a final row. Numeric fields, including zero-value amounts, are written explicitly so payroll columns are not left blank when their calculated value is zero. Data rows are written as values, not copied from the sample’s formulas, so generated workbooks do not retain sample formulas or cached results.

---
This document provides a comprehensive overview of the timesheet muster master sheet generation logic within your application, reflecting the latest changes.
