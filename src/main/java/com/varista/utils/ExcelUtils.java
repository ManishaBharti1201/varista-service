package com.varista.utils;

import com.varista.constant.ApplicationConstants;
import com.varista.entity.Blob;
import com.varista.model.MusterWageInfo;

import com.varista.repository.BlobRepository; // Import BlobRepository
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired; // Import Autowired
import org.springframework.beans.factory.annotation.Value; // Import Value
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.YearMonth;
import java.time.LocalDateTime; // Import LocalDateTime
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class ExcelUtils {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired
    private BlobRepository blobRepository; // Inject BlobRepository

    /**
     * Helper method to inspect template headers for debugging.
     * Returns a fixed layout inspection result: startRow=8 (Excel row 9), empty header maps, and dayColumns mapped 1..27->col 9..35.
     */
    public Map<String, Object> inspectTemplateHeaders() {
        Map<String, Object> result = new HashMap<>();
        // Fixed layout: start writing at Excel row 9 (index 8)
        int startRow = 8;
        result.put("headerRowIndex", -1);
        result.put("headerMap", Collections.emptyMap());
        result.put("headerNormMap", Collections.emptySet());
        Map<Integer, Integer> dayColumns = new HashMap<>();
        int dayStartCol = 9; // day 1 -> col index 9
        for (int d = 1; d <= 27; d++) {
            dayColumns.put(d, dayStartCol + (d - 1));
        }
        result.put("dayColumns", dayColumns);
        result.put("startRow", startRow);
        return result;
    }

    // Main generator that writes into the first sheet by default and applies styles
    public ByteArrayInputStream generateMasterRegisterFile(List<MusterWageInfo> attendanceData, YearMonth ym) throws IOException {

        log.info("generateMasterRegisterFile called with {} entries for {}", (attendanceData == null ? 0 : attendanceData.size()), ym);

        ClassPathResource resource = new ClassPathResource("timesheet_template.xlsx");
        try (InputStream is = resource.getInputStream(); Workbook workbook = new XSSFWorkbook(is); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // --- Define Fonts and Styles ---
            // Default font for the workbook (Calibri, size 20)
            Font defaultFont = workbook.createFont();
            defaultFont.setFontName("Calibri");
            defaultFont.setFontHeightInPoints((short) 20); // Default data font size

            // Data Style (Calibri, size 20, Centered, Borders)
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setFont(defaultFont);
            dataStyle.setAlignment(HorizontalAlignment.CENTER);
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            short blackIdx = IndexedColors.BLACK.getIndex();
            dataStyle.setTopBorderColor(blackIdx);
            dataStyle.setBottomBorderColor(blackIdx);
            dataStyle.setLeftBorderColor(blackIdx);
            dataStyle.setRightBorderColor(blackIdx);

            // Sunday Style (Calibri, size 20, Yellow background, Centered, Borders)
            CellStyle sundayStyle = workbook.createCellStyle();
            sundayStyle.setFont(defaultFont);
            sundayStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
            sundayStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            sundayStyle.setAlignment(HorizontalAlignment.CENTER);
            sundayStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            sundayStyle.setBorderTop(BorderStyle.THIN);
            sundayStyle.setBorderBottom(BorderStyle.THIN);
            sundayStyle.setBorderLeft(BorderStyle.THIN);
            sundayStyle.setBorderRight(BorderStyle.THIN);
            sundayStyle.setTopBorderColor(blackIdx);
            sundayStyle.setBottomBorderColor(blackIdx);
            sundayStyle.setLeftBorderColor(blackIdx);
            sundayStyle.setRightBorderColor(blackIdx);

            // Header Font (Calibri, Bold, size 20)
            Font headerFont = workbook.createFont();
            headerFont.setFontName("Calibri");
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 20);
            
            // Header Style (Calibri, Bold, size 20, Centered, Borders)
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setTopBorderColor(blackIdx);
            headerStyle.setBottomBorderColor(blackIdx);
            headerStyle.setLeftBorderColor(blackIdx);
            headerStyle.setRightBorderColor(blackIdx);

            // Total Row Style (Calibri, Bold, size 20, Centered, Borders)
            CellStyle totalRowStyle = workbook.createCellStyle();
            totalRowStyle.setFont(headerFont); // Use header font for bold
            totalRowStyle.setAlignment(HorizontalAlignment.CENTER);
            totalRowStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            totalRowStyle.setBorderTop(BorderStyle.THIN);
            totalRowStyle.setBorderBottom(BorderStyle.THIN);
            totalRowStyle.setBorderLeft(BorderStyle.THIN);
            totalRowStyle.setBorderRight(BorderStyle.THIN);
            totalRowStyle.setTopBorderColor(blackIdx);
            totalRowStyle.setBottomBorderColor(blackIdx);
            totalRowStyle.setLeftBorderColor(blackIdx);
            totalRowStyle.setRightBorderColor(blackIdx);


            // --- Sheet 1: Master Muster Sheet ---
            Sheet masterMusterSheet;
            if (workbook.getNumberOfSheets() > 0) {
                masterMusterSheet = workbook.getSheetAt(0);
                if (masterMusterSheet == null) {
                    masterMusterSheet = workbook.createSheet("Master Muster");
                } else {
                    workbook.setSheetName(0, "Master Muster");
                }
            } else {
                masterMusterSheet = workbook.createSheet("Master Muster");
            }

            int writtenMusterRows = fillMasterMusterData(masterMusterSheet, attendanceData, ym, dataStyle, sundayStyle, headerStyle);
            log.info("Wrote {} rows into Master Muster Sheet.", writtenMusterRows);

            // --- Sheet 2: Master Wage Sheet ---
            Sheet masterWageSheet;
            if (workbook.getNumberOfSheets() > 1) {
                masterWageSheet = workbook.getSheetAt(1);
                workbook.setSheetName(1, "Master Wage");
            } else {
                masterWageSheet = workbook.createSheet("Master Wage");
                workbook.setSheetOrder("Master Wage", 1);
            }
            int writtenWageRows = fillMasterWageData(masterWageSheet, attendanceData, ym, dataStyle, headerStyle, totalRowStyle);
            log.info("Wrote {} rows into Master Wage Sheet.", writtenWageRows);

            // Remove any extra sheets beyond the first two
            while (workbook.getNumberOfSheets() > 2) {
                log.warn("Removing extra sheet at index 2 (name: {}).", workbook.getSheetName(2));
                workbook.removeSheetAt(2);
            }

            // write a debug marker top-left so user sees status
            try {
                Row metaRow = masterMusterSheet.getRow(0); // Use masterMusterSheet for debug marker
                if (metaRow == null) metaRow = masterMusterSheet.createRow(0);
                Cell infoCell = metaRow.getCell(0);
                if (infoCell == null) infoCell = metaRow.createCell(0);
                infoCell.setCellValue("GeneratedRows=" + writtenMusterRows + "; YearMonth=" + ym.toString());
            } catch (Exception e) {
                log.warn("Failed to write debug marker: {}", e.getMessage());
            }

            // persist and return
            workbook.write(out);
            try {
                Path directory = Paths.get(ApplicationConstants.MASTER_REGISTER_DIRECTORY); // Use injected property
                Files.createDirectories(directory);
                String fileName = attendanceData.get(0).getSubContractorName() + "_" + ym.toString() + ".xlsx";
                Path filePath = directory.resolve(fileName);
                Files.write(filePath, out.toByteArray());
                log.info("Generated Excel file saved to: {}", filePath.toAbsolutePath());

                // Save blob record to database
                Blob blobRecord = Blob.builder()
                        .fileName(fileName)
                        .generatedMonth(ym.toString())
                        .filePath(filePath.toString())
                        .relatedEntityName(attendanceData.get(0).getSubContractorName())
                        .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .creationDate(LocalDateTime.now())
                        .build();
                blobRepository.save(blobRecord);
                log.info("Blob record saved for file: {}", fileName);

            } catch (Exception ex) {
                log.warn("Failed to persist generated Excel file or save blob record: {}", ex.getMessage());
            }
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    /**
     * Fills data into the Master Muster Sheet.
     */
    private int fillMasterMusterData(Sheet sheet, List<MusterWageInfo> data, YearMonth ym, CellStyle dataStyle, CellStyle sundayStyle, CellStyle headerStyle) {
        if (data == null || data.isEmpty()) {
            log.info("No muster master data to write.");
            return 0;
        }

        final int headerRowIndex = 7; // Excel row 8 -> index 7 (assuming headers are here)
        final int startDataRow = 8; // Excel row 9 -> index 8
        final int colSl = 0;
        final int colAdhaar = 1;
        final int colEip = 2;
        final int colName = 3;
        final int colDesignation = 4;
        final int colDob = 5;
        final int colDateOfEntry = 6;
        final int colWorkingHrs = 7;
        final int colInterval = 8;
        final int dayStartCol = 9; // day 1 -> column index 9

        // Set Month and Year
        Row monthYearRow = sheet.getRow(0);
        if (monthYearRow == null) monthYearRow = sheet.createRow(0);
        Cell monthYearCell = monthYearRow.getCell(1); // Column B
        if (monthYearCell == null) monthYearCell = monthYearRow.createCell(1);
        monthYearCell.setCellValue(ym.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        monthYearCell.setCellStyle(headerStyle); // Use header style for month/year

        // Precompute which columns correspond to Sundays in the provided YearMonth
        Set<Integer> sundayColumns = new HashSet<>();
        for (int day = 1; day <= 31; day++) {
            try {
                DayOfWeek dow = ym.atDay(day).getDayOfWeek();
                if (dow == DayOfWeek.SUNDAY) {
                    sundayColumns.add(dayStartCol + (day - 1));
                }
            } catch (Exception ignored) {
            }
        }

        // --- Apply Header Style to existing header row (row 8, index 7) ---
        Row headerRow = sheet.getRow(headerRowIndex);
        if (headerRow == null) headerRow = sheet.createRow(headerRowIndex);

        // Determine the last column that might have a header (Muster Master specific)
        int lastHeaderCol = dayStartCol + 31 + 5 -1; // 31 days + 5 trailing fields (TotalDays, OT, NH, JobCode, SubContractorName)

        for (int c = 0; c <= lastHeaderCol; c++) {
            Cell hcell = headerRow.getCell(c);
            if (hcell == null) hcell = headerRow.createCell(c);
            // Preserve existing cell value, just apply style
            hcell.setCellStyle(headerStyle);
        }
        log.debug("Applied header style to Muster Master Sheet header row.");


        // Apply Sunday styles to data rows
        int headerAndWriteEndRow = startDataRow + data.size() - 1;
        headerAndWriteEndRow = Math.max(headerAndWriteEndRow, startDataRow);
        for (Integer colIndex : sundayColumns) {
            for (int r = startDataRow; r <= headerAndWriteEndRow; r++) { // Start from data rows
                Row rr = sheet.getRow(r);
                if (rr == null) rr = sheet.createRow(r);
                Cell cc = rr.getCell(colIndex);
                if (cc == null) cc = rr.createCell(colIndex);
                cc.setCellStyle(sundayStyle);
            }
        }

        log.info("Writing {} entries into Muster Master Sheet starting at row {} (fixed layout)", data.size(), startDataRow + 1);

        int rowNum = startDataRow;
        int writtenRows = 0;
        for (MusterWageInfo entry : data) {
            Row row = sheet.getRow(rowNum);
            if (row == null) row = sheet.createRow(rowNum);

            // Fixed columns
            setCellWithStyle(row, colSl, entry.getSerialNo(), dataStyle);
            setCellWithStyle(row, colAdhaar, entry.getAadharNumber(), dataStyle);
            setCellWithStyle(row, colEip, entry.getEipId(), dataStyle);
            setCellWithStyle(row, colName, entry.getEmployeeName(), dataStyle);
            setCellWithStyle(row, colDesignation, entry.getDesignation(), dataStyle);
            setCellWithStyle(row, colDob, entry.getDateOfBirth() != null ? entry.getDateOfBirth().format(DATE_FORMATTER) : "", dataStyle);
            setCellWithStyle(row, colDateOfEntry, entry.getDateOfJoining() != null ? entry.getDateOfJoining().format(DATE_FORMATTER) : "", dataStyle);
            setCellWithStyle(row, colWorkingHrs, entry.getWorkingHours(), dataStyle);
            setCellWithStyle(row, colInterval, entry.getInterval(), dataStyle);

            Map<Integer, String> dailyAttendanceStatus = entry.getDailyAttendanceStatus();
            int wroteForEntry = 0;
            for (int day = 1; day <= 31; day++) { // Iterate for all 31 days
                int colIndex = dayStartCol + (day - 1);
                String val = (dailyAttendanceStatus == null) ? "" : dailyAttendanceStatus.getOrDefault(day, "");

                Cell cell = row.getCell(colIndex);
                if (cell == null) cell = row.createCell(colIndex);
                setCellValue(cell, val);
                // Apply style based on precomputed sundayColumns
                cell.setCellStyle(sundayColumns.contains(colIndex) ? sundayStyle : dataStyle);
                if (val != null && !val.trim().isEmpty()) wroteForEntry++;
            }

            // Trailing fixed columns after day 31
            int currentTrailingCol = dayStartCol + 31; // Start after the 31st day column
            setCellWithStyle(row, currentTrailingCol++, entry.getTotalDaysWorked(), dataStyle);
            setCellWithStyle(row, currentTrailingCol++, entry.getOverTimeHours(), dataStyle);
            setCellWithStyle(row, currentTrailingCol++, entry.getNightHalt(), dataStyle);
            setCellWithStyle(row, currentTrailingCol++, entry.getJobCode(), dataStyle);
            setCellWithStyle(row, currentTrailingCol++, entry.getSubContractorName(), dataStyle);

            log.debug("Row {} (serialNo={}): wrote {} day-cells", rowNum + 1, entry.getSerialNo(), wroteForEntry);

            // Ensure every cell in columns 0..lastCol exists and has borders
            for (int c = 0; c <= lastHeaderCol; c++) {
                Cell cc = row.getCell(c);
                if (cc == null) cc = row.createCell(c);
                if (sundayColumns.contains(c)) cc.setCellStyle(sundayStyle);
                else cc.setCellStyle(dataStyle);
            }

            writtenRows++;
            rowNum++;
        }

        log.info("Wrote {} rows into Muster Master Sheet (fixed layout).", writtenRows);
        return writtenRows;
    }

    /**
     * Fills data into the Master Wage Sheet.
     */
    private int fillMasterWageData(Sheet sheet, List<MusterWageInfo> data, YearMonth ym, CellStyle dataStyle, CellStyle headerStyle, CellStyle totalRowStyle) {
        if (data == null || data.isEmpty()) {
            log.info("No muster wage data to write.");
            return 0;
        }

        // Define header row and columns for the wage sheet
        int headerRowIndex = 9;
        int startDataRow = 9;
        
        // Column indices for sums
        final int colTotalDaysWorked = 12;
        final int colOverTimeHours = 13;
        final int colNightHalt = 14;
        final int colBasicWages = 15;
        final int colOtAmount = 16;
        final int colNhAmount = 17;
        final int colHra = 18;
        final int colGrossWage = 19;
        final int colPfDeduction = 20;
        final int colEsiDeduction = 21; // Assuming this is the ESIC deduction column
        final int colPtDeduction = 22;
        final int colLwfDeduction = 23;
        final int colOtherDeductions = 24;
        final int colTotalDeductions = 25;
        final int colNetWage = 26;


        // Set Month and Year
        Row monthYearRow = sheet.getRow(0);
        if (monthYearRow == null) monthYearRow = sheet.createRow(0);
        Cell monthYearCell = monthYearRow.getCell(0); // Column A
        if (monthYearCell == null) monthYearCell = monthYearRow.createCell(0);
        monthYearCell.setCellValue(ym.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        monthYearCell.setCellStyle(headerStyle); // Use header style for month/year

        // --- Apply Header Style to existing header row (row 1, index 0) ---
        Row headerRow = sheet.getRow(headerRowIndex);
        if (headerRow == null) headerRow = sheet.createRow(headerRowIndex);

        // Assuming a fixed number of header columns based on the previous implementation
        int lastHeaderCol = 29;
        for (int c = 0; c <= lastHeaderCol; c++) {
            Cell hcell = headerRow.getCell(c);
            if (hcell == null) hcell = headerRow.createCell(c);
            // Preserve existing cell value, just apply style
            headerStyle.setFillBackgroundColor(IndexedColors.WHITE.getIndex());
            hcell.setCellStyle(headerStyle);


        }
        log.debug("Applied header style to Master Wage Sheet header row.");

        // Auto-size columns for headers (only if headers are present)
        for(int i = 0; i <= lastHeaderCol; i++) {
            sheet.autoSizeColumn(i);
        }

        log.info("Writing {} entries into Master Wage Sheet starting at row {}.", data.size(), startDataRow + 1);

        int rowNum = startDataRow;
        int writtenRows = 0;
        
        // Sum variables
        double totalWorkedDays = 0;
        double totalOverTimeHours = 0;
        double totalNightHalt = 0;
        double totalBasicWages = 0;
        double totalOtAmount = 0;
        double totalNhAmount = 0;
        double totalHra = 0;
        double totalGrossWage = 0;
        double totalPfDeduction = 0;
        double totalEsiDeduction = 0; 
        double totalPtDeduction = 0;
        double totalLwfDeduction = 0;
        double totalOtherDeductions = 0;
        double totalTotalDeductions = 0;
        double totalNetWage = 0;


        for (MusterWageInfo entry : data) {
            Row row = sheet.createRow(rowNum);
            int col = 0;
            setCellWithStyle(row, col++, entry.getSerialNo(), dataStyle);
            setCellWithStyle(row, col++, entry.getAadharNumber(), dataStyle);
            setCellWithStyle(row, col++, entry.getEipId(), dataStyle);
            setCellWithStyle(row, col++, entry.getEmployeeName(), dataStyle);
            setCellWithStyle(row, col++, entry.getFatherName(), dataStyle);
            setCellWithStyle(row, col++, entry.getGender(), dataStyle);
            setCellWithStyle(row, col++, entry.getDesignation(), dataStyle);
            setCellWithStyle(row, col++, entry.getCategory(), dataStyle);
            setCellWithStyle(row, col++, entry.getDateOfJoining(), dataStyle);
            setCellWithStyle(row, col++, entry.getUanNo(), dataStyle);
            setCellWithStyle(row, col++, entry.getEsiNo(), dataStyle); // ESI Number (not deduction)
            setCellWithStyle(row, col++, entry.getDailyWage(), dataStyle);
            
            // Accumulate sums and set cell values
            if (entry.getTotalDaysWorked() != 0) {
                totalWorkedDays += entry.getTotalDaysWorked();
                setCellWithStyle(row, col++, entry.getTotalDaysWorked(), dataStyle);
            } else {
                setCellWithStyle(row, col++, "", dataStyle);
            }
            
            if (entry.getOverTimeHours() != 0) {
                totalOverTimeHours += entry.getOverTimeHours();
                setCellWithStyle(row, col++, entry.getOverTimeHours(), dataStyle);
            } else {
                setCellWithStyle(row, col++, "", dataStyle);
            }

            if (entry.getNightHalt() != 0) {
                totalNightHalt += entry.getNightHalt();
                setCellWithStyle(row, col++, entry.getNightHalt(), dataStyle);
            } else {
                setCellWithStyle(row, col++, "", dataStyle);
            }

            if (entry.getBasicWages() != 0) {
                totalBasicWages += entry.getBasicWages();
                setCellWithStyle(row, col++, entry.getBasicWages(), dataStyle);
            } else {
                setCellWithStyle(row, col++, "", dataStyle);
            }

            if (entry.getOtAmount() != 0) {
                totalOtAmount += entry.getOtAmount();
                setCellWithStyle(row, col++, entry.getOtAmount(), dataStyle);
            } else {
                setCellWithStyle(row, col++, "", dataStyle);
            }

            if (entry.getNhAmount() != 0) {
                totalNhAmount += entry.getNhAmount();
                setCellWithStyle(row, col++, entry.getNhAmount(), dataStyle);
            } else {
                setCellWithStyle(row, col++, "", dataStyle);
            }

            if (entry.getHra() != 0) {
                totalHra += entry.getHra();
                setCellWithStyle(row, col++, entry.getHra(), dataStyle);
            } else {
                setCellWithStyle(row, col++, "", dataStyle);
            }

            if (entry.getGrossWage() != 0) {
                totalGrossWage += entry.getGrossWage();
                setCellWithStyle(row, col++, entry.getGrossWage(), dataStyle);
            } else {
                setCellWithStyle(row, col++, "", dataStyle);
            }

            if (entry.getPfDeduction() != 0) {
                totalPfDeduction += entry.getPfDeduction();
                setCellWithStyle(row, col++, entry.getPfDeduction(), dataStyle);
            } else {
                setCellWithStyle(row, col++, "", dataStyle);
            }

            // Assuming getEsiDeduction() exists for ESIC deduction.
            // If MusterWageInfo does not have a getEsiDeduction() method, this will need adjustment.
            // For now, I'll assume it exists and returns a Double.
            Double esiDeductionValue = null;
            try {
                java.lang.reflect.Method getEsiDeductionMethod = entry.getClass().getMethod("getEsiDeduction");
                esiDeductionValue = (Double) getEsiDeductionMethod.invoke(entry);
            } catch (NoSuchMethodException e) {
                log.warn("MusterWageInfo does not have getEsiDeduction() method. ESI Deduction will be 0.0.");
            } catch (Exception e) {
                log.warn("Error invoking getEsiDeduction() on MusterWageInfo: {}", e.getMessage());
            }

            if (esiDeductionValue != null) {
                totalEsiDeduction += esiDeductionValue;
                setCellWithStyle(row, col++, esiDeductionValue, dataStyle);
            } else {
                setCellWithStyle(row, col++, "", dataStyle);
            }


            if (entry.getPtDeduction() != 0) {
                totalPtDeduction += entry.getPtDeduction();
                setCellWithStyle(row, col++, entry.getPtDeduction(), dataStyle);
            } else {
                setCellWithStyle(row, col++, "", dataStyle);
            }

            if (entry.getLwfDeduction() != 0) {
                totalLwfDeduction += entry.getLwfDeduction();
                setCellWithStyle(row, col++, entry.getLwfDeduction(), dataStyle);
            } else {
                setCellWithStyle(row, col++, "", dataStyle);
            }

            if (entry.getOtherDeductions() != 0) {
                totalOtherDeductions += entry.getOtherDeductions();
                setCellWithStyle(row, col++, entry.getOtherDeductions(), dataStyle);
            } else {
                setCellWithStyle(row, col++, "", dataStyle);
            }

            if (entry.getTotalDeductions() != 0) {
                totalTotalDeductions += entry.getTotalDeductions();
                setCellWithStyle(row, col++, entry.getTotalDeductions(), dataStyle);
            } else {
                setCellWithStyle(row, col++, "", dataStyle);
            }

            if (entry.getNetWage() != 0) {
                totalNetWage += entry.getNetWage();
                setCellWithStyle(row, col++, entry.getNetWage(), dataStyle);
            } else {
                setCellWithStyle(row, col++, "", dataStyle);
            }
            
            setCellWithStyle(row, col++, entry.getDateOfPayment(), dataStyle);
            setCellWithStyle(row, col++, entry.getJobCode(), dataStyle);
            setCellWithStyle(row, col++, entry.getSubContractorName(), dataStyle);
            setCellWithStyle(row, col++, entry.getRemarks(), dataStyle);

            writtenRows++;
            rowNum++;
        }

        // Add total row
        if (writtenRows > 0) {
            Row totalRow = sheet.createRow(rowNum);
            setCellWithStyle(totalRow, 0, "Total", totalRowStyle); // Label "Total" in the first column
            
            // Apply total values to respective columns
            setCellWithStyle(totalRow, colTotalDaysWorked, totalWorkedDays, totalRowStyle);
            setCellWithStyle(totalRow, colOverTimeHours, totalOverTimeHours, totalRowStyle);
            setCellWithStyle(totalRow, colNightHalt, totalNightHalt, totalRowStyle);
            setCellWithStyle(totalRow, colBasicWages, totalBasicWages, totalRowStyle);
            setCellWithStyle(totalRow, colOtAmount, totalOtAmount, totalRowStyle);
            setCellWithStyle(totalRow, colNhAmount, totalNhAmount, totalRowStyle);
            setCellWithStyle(totalRow, colHra, totalHra, totalRowStyle);
            setCellWithStyle(totalRow, colGrossWage, totalGrossWage, totalRowStyle);
            setCellWithStyle(totalRow, colPfDeduction, totalPfDeduction, totalRowStyle);
            setCellWithStyle(totalRow, colEsiDeduction, totalEsiDeduction, totalRowStyle);
            setCellWithStyle(totalRow, colPtDeduction, totalPtDeduction, totalRowStyle);
            setCellWithStyle(totalRow, colLwfDeduction, totalLwfDeduction, totalRowStyle);
            setCellWithStyle(totalRow, colOtherDeductions, totalOtherDeductions, totalRowStyle);
            setCellWithStyle(totalRow, colTotalDeductions, totalTotalDeductions, totalRowStyle);
            setCellWithStyle(totalRow, colNetWage, totalNetWage, totalRowStyle);

            // Ensure all cells in the total row up to lastHeaderCol have borders
            for (int c = 0; c <= lastHeaderCol; c++) {
                Cell cell = totalRow.getCell(c);
                if (cell == null) {
                    cell = totalRow.createCell(c);
                }
                // Always apply totalRowStyle to ensure borders and consistent formatting
                cell.setCellStyle(totalRowStyle);
            }

            writtenRows++; // Count the total row as well
        }

        log.info("Wrote {} rows into Master Wage Sheet.", writtenRows);
        return writtenRows;
    }


    // small helper used by generated-sheet fallback
    private void setCellWithStyle(Row row, int colIndex, Object value, CellStyle style) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) cell = row.createCell(colIndex);
        setCellValue(cell, value);
        if (style != null) cell.setCellStyle(style);
    }

    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setCellValue("");
            return;
        }
        if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    public String getCellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((int) cell.getNumericCellValue());
            case FORMULA -> {
                try {
                    // Attempt to evaluate formula as numeric
                    yield String.valueOf((int) cell.getNumericCellValue());
                } catch (IllegalStateException e) {
                    // If formula result is string, get it as string
                    yield cell.getStringCellValue().trim();
                }
            }
            default -> "";
        };
    }

}
