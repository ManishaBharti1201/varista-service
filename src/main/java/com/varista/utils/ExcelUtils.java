package com.varista.utils;

import com.varista.model.MusterSheetInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.YearMonth;
import java.util.*;

@Slf4j
@Service
public class ExcelUtils {

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

    // New main generator that writes into the first sheet by default and applies styles
    public ByteArrayInputStream generateAttendanceSheet(List<MusterSheetInfo> attendanceData, YearMonth ym) throws IOException {

        log.info("generateAttendanceSheet called with {} entries for {}", (attendanceData == null ? 0 : attendanceData.size()), ym);

        ClassPathResource resource = new ClassPathResource("timesheet_template.xlsx");
        try (InputStream is = resource.getInputStream(); Workbook workbook = new XSSFWorkbook(is); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // Create styles: data font size 20 and Sunday yellow fill
            Font dataFont = workbook.createFont();
            dataFont.setFontHeightInPoints((short) 20);
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setFont(dataFont);
            // center align text
            dataStyle.setAlignment(HorizontalAlignment.CENTER);
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            // set thin black borders for the data style
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            short blackIdx = IndexedColors.BLACK.getIndex();
            dataStyle.setTopBorderColor(blackIdx);
            dataStyle.setBottomBorderColor(blackIdx);
            dataStyle.setLeftBorderColor(blackIdx);
            dataStyle.setRightBorderColor(blackIdx);

            CellStyle sundayStyle = workbook.createCellStyle();
            sundayStyle.setFont(dataFont);
            sundayStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
            sundayStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            sundayStyle.setAlignment(HorizontalAlignment.CENTER);
            sundayStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            // set thin black borders for the sunday style as well
            sundayStyle.setBorderTop(BorderStyle.THIN);
            sundayStyle.setBorderBottom(BorderStyle.THIN);
            sundayStyle.setBorderLeft(BorderStyle.THIN);
            sundayStyle.setBorderRight(BorderStyle.THIN);
            sundayStyle.setTopBorderColor(blackIdx);
            sundayStyle.setBottomBorderColor(blackIdx);
            sundayStyle.setLeftBorderColor(blackIdx);
            sundayStyle.setRightBorderColor(blackIdx);

            // 1) Use the first sheet by default
            Sheet attendanceSheet;
            if (workbook.getNumberOfSheets() > 0) {
                attendanceSheet = workbook.getSheetAt(0);
            } else {
                attendanceSheet = workbook.createSheet("Sheet1");
            }

            // 2) Fill data into this sheet
            int written = fillDataStartingAtRow(attendanceSheet, attendanceData, ym, dataStyle, sundayStyle);

            // 3) If nothing written into template first sheet, create a generated sheet and write there (fallback)
            if (written == 0 && attendanceData != null && !attendanceData.isEmpty()) {
                String genName = "Generated Master Attendance";
                Sheet genSheet = workbook.getSheet(genName);
                if (genSheet != null) workbook.removeSheetAt(workbook.getSheetIndex(genName));
                genSheet = workbook.createSheet(genName);

                int rowNum = 8; // Excel row 9 index
                int fallbackWritten = 0;
                for (MusterSheetInfo entry : attendanceData) {
                    Row row = genSheet.getRow(rowNum);
                    if (row == null) row = genSheet.createRow(rowNum);
                    int c = 0;
                    setCellWithStyle(row, c++, entry.getSlNo(), dataStyle);
                    setCellWithStyle(row, c++, entry.getAdhaar(), dataStyle);
                    setCellWithStyle(row, c++, entry.getEip(), dataStyle);
                    setCellWithStyle(row, c++, entry.getEmployeeName(), dataStyle);
                    setCellWithStyle(row, c++, entry.getDesignation(), dataStyle);
                    setCellWithStyle(row, c++, entry.getDob(), dataStyle);
                    setCellWithStyle(row, c++, entry.getDateOfEntry(), dataStyle);
                    setCellWithStyle(row, c++, entry.getWorkingHrs(), dataStyle);
                    setCellWithStyle(row, c++, entry.getInterval(), dataStyle);

                    Map<Integer, String> cal = entry.getCalender();
                    for (int day = 1; day <= 27; day++) {
                        String val = (cal == null) ? "" : cal.getOrDefault(day, "");
                        DayOfWeek dow = DayOfWeek.MONDAY;
                        try {
                            dow = ym.atDay(day).getDayOfWeek();
                        } catch (Exception ignored) {
                        }
                        CellStyle style = (dow == DayOfWeek.SUNDAY) ? sundayStyle : dataStyle;
                        setCellWithStyle(row, c++, val, style);
                    }

                    // create empty bordered cells for days 28..31 so table shows borders
                    int fallbackDayStartCol = 9;
                    for (int day = 28; day <= 31; day++) {
                        int colIndex = fallbackDayStartCol + (day - 1);
                        Cell blank = row.getCell(colIndex);
                        if (blank == null) blank = row.createCell(colIndex);
                        DayOfWeek dd = DayOfWeek.MONDAY;
                        try {
                            dd = ym.atDay(day).getDayOfWeek();
                        } catch (Exception ignored) {
                        }
                        if (dd == DayOfWeek.SUNDAY) blank.setCellStyle(sundayStyle);
                        else blank.setCellStyle(dataStyle);
                    }

                    // leave columns for days 28-31 blank (skip 4 columns) before writing totals
                    c += 4;

                    setCellWithStyle(row, c++, entry.getTotalDays(), dataStyle);
                    setCellWithStyle(row, c++, entry.getOtHrs(), dataStyle);
                    setCellWithStyle(row, c++, entry.getNhHrs(), dataStyle);
                    setCellWithStyle(row, c++, entry.getJobCode(), dataStyle);
                    setCellWithStyle(row, c, entry.getContractorName(), dataStyle);

                    // Ensure every cell in columns 0..lastCol exists and has borders (sundayStyle for sundays)
                    int trailing = 5; // totals
                    int last = fallbackDayStartCol + 31 + (trailing - 1);
                    for (int ccIdx = 0; ccIdx <= last; ccIdx++) {
                        Cell ccCell = row.getCell(ccIdx);
                        if (ccCell == null) ccCell = row.createCell(ccIdx);
                        DayOfWeek maybe = DayOfWeek.MONDAY;
                        int dayNum = ccIdx - fallbackDayStartCol + 1;
                        if (dayNum >= 1 && dayNum <= 27) {
                            try {
                                maybe = ym.atDay(dayNum).getDayOfWeek();
                            } catch (Exception ignored) {
                            }
                        }
                        if (maybe == DayOfWeek.SUNDAY) ccCell.setCellStyle(sundayStyle);
                        else ccCell.setCellStyle(dataStyle);
                    }

                    rowNum++;
                    fallbackWritten++;
                }
                written = fallbackWritten;
                log.info("Hard fallback wrote {} rows into {}", fallbackWritten, genName);
            }

            // write a debug marker top-left so user sees status
            try {
                Row metaRow = attendanceSheet.getRow(0);
                if (metaRow == null) metaRow = attendanceSheet.createRow(0);
                Cell infoCell = metaRow.getCell(0);
                if (infoCell == null) infoCell = metaRow.createCell(0);
                infoCell.setCellValue("GeneratedRows=" + written + "; YearMonth=" + ym.toString());
            } catch (Exception e) {
                log.warn("Failed to write debug marker: {}", e.getMessage());
            }

            // persist and return
            workbook.write(out);
            try {
                Path directory = Paths.get("./Monthly_Sheet");

                // Create the directory (and any missing parent directories) if they don't exist
                Files.createDirectories(directory);

                Path filePath = directory.resolve("Syncra_Monthly_Sheet_" + System.currentTimeMillis() + ".xlsx");

                Files.write(filePath, out.toByteArray());
            } catch (Exception ex) {
                log.warn("persist failed: {}", ex.getMessage());
            }
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    /**
     * Simplified writer: write data starting at Excel row 9 (index 8) and fixed columns starting at col 0.
     * Writes 27 day columns (days 1..27) starting at fixed column index 9.
     */
    private int fillDataStartingAtRow(Sheet sheet, List<MusterSheetInfo> data, YearMonth ym, CellStyle dataStyle, CellStyle sundayStyle) {
        if (data == null || data.isEmpty()) {
            log.info("No attendance data to write");
            return 0;
        }

        final int startRow = 8; // Excel row 9 -> index 8
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
        final int daysToWrite = 27;

        // Precompute which columns correspond to Sundays in the provided YearMonth
        Set<Integer> sundayColumns = new HashSet<>();
        for (int day = 1; day <= daysToWrite; day++) {
            try {
                DayOfWeek dow = ym.atDay(day).getDayOfWeek();
                if (dow == DayOfWeek.SUNDAY) {
                    sundayColumns.add(dayStartCol + (day - 1));
                }
            } catch (Exception ignored) {
            }
        }

        // Paint the sunday columns' cells from the column header row through the rows we'll write
        int headerAndWriteEndRow = startRow + data.size() - 1;
        headerAndWriteEndRow = Math.max(headerAndWriteEndRow, startRow); // at least startRow
        int headerRowIndex = Math.max(0, startRow - 1); // paint starting at the header row (row above startRow)
        for (Integer colIndex : sundayColumns) {
            for (int r = headerRowIndex; r <= headerAndWriteEndRow; r++) {
                Row rr = sheet.getRow(r);
                if (rr == null) rr = sheet.createRow(r);
                Cell cc = rr.getCell(colIndex);
                if (cc == null) cc = rr.createCell(colIndex);
                // apply sunday style (centered + yellow)
                cc.setCellStyle(sundayStyle);
            }
        }

        // Ensure header row has borders for the full table range (columns 0 .. lastCol)
        int trailingFields = 5; // TotalDays, OT, NH, JobCode, Contractor
        int lastCol = dayStartCol + 31 + (trailingFields - 1); // dayStartCol + 31 .. last totals col
        Row headerRow = sheet.getRow(headerRowIndex);
        if (headerRow == null) headerRow = sheet.createRow(headerRowIndex);
        for (int c = 0; c <= lastCol; c++) {
            Cell hcell = headerRow.getCell(c);
            if (hcell == null) hcell = headerRow.createCell(c);
            // don't overwrite sunday style for sunday columns
            if (sundayColumns.contains(c)) continue;
            // apply data style (borders + center) to header cells
            hcell.setCellStyle(dataStyle);
        }

        log.info("Writing {} entries starting at row {} and column {} (fixed layout)", data.size(), startRow + 1, 0);

        int rowNum = startRow;
        int writtenRows = 0;
        for (MusterSheetInfo entry : data) {
            Row row = sheet.getRow(rowNum);
            if (row == null) row = sheet.createRow(rowNum);

            // Fixed columns
            setCellWithStyle(row, colSl, entry.getSlNo(), dataStyle);
            setCellWithStyle(row, colAdhaar, entry.getAdhaar(), dataStyle);
            setCellWithStyle(row, colEip, entry.getEip(), dataStyle);
            setCellWithStyle(row, colName, entry.getEmployeeName(), dataStyle);
            setCellWithStyle(row, colDesignation, entry.getDesignation(), dataStyle);
            setCellWithStyle(row, colDob, entry.getDob(), dataStyle);
            setCellWithStyle(row, colDateOfEntry, entry.getDateOfEntry(), dataStyle);
            setCellWithStyle(row, colWorkingHrs, entry.getWorkingHrs(), dataStyle);
            setCellWithStyle(row, colInterval, entry.getInterval(), dataStyle);

            Map<Integer, String> cal = entry.getCalender();
            int wroteForEntry = 0;
            for (int day = 1; day <= daysToWrite; day++) {
                int colIndex = dayStartCol + (day - 1);
                String val = (cal == null) ? "" : cal.getOrDefault(day, "");
                DayOfWeek dow = DayOfWeek.MONDAY;
                try {
                    dow = ym.atDay(day).getDayOfWeek();
                } catch (Exception ignored) {
                }
                Cell cell = row.getCell(colIndex);
                if (cell == null) cell = row.createCell(colIndex);
                setCellValue(cell, val);
                cell.setCellStyle(dow == DayOfWeek.SUNDAY ? sundayStyle : dataStyle);
                if (val != null && !val.trim().isEmpty()) wroteForEntry++;
            }

            // Ensure columns for days 28..31 exist and have borders (empty cells)
            for (int day = daysToWrite + 1; day <= 31; day++) {
                int colIndex = dayStartCol + (day - 1);
                Cell blank = row.getCell(colIndex);
                if (blank == null) blank = row.createCell(colIndex);
                // if this column is Sunday (unlikely for days >27 in our map) apply sundayStyle, else dataStyle
                if (sundayColumns.contains(colIndex)) blank.setCellStyle(sundayStyle);
                else blank.setCellStyle(dataStyle);
            }

            // trailing fixed columns after day 31 (leave days 28-31 blank)
            int afterDays = dayStartCol + 31;
            setCellWithStyle(row, afterDays++, entry.getTotalDays(), dataStyle);
            setCellWithStyle(row, afterDays++, entry.getOtHrs(), dataStyle);
            setCellWithStyle(row, afterDays++, entry.getNhHrs(), dataStyle);
            setCellWithStyle(row, afterDays++, entry.getJobCode(), dataStyle);
            setCellWithStyle(row, afterDays, entry.getContractorName(), dataStyle);

            log.debug("Row {} (slNo={}): wrote {} day-cells", rowNum + 1, entry.getSlNo(), wroteForEntry);

            // Ensure every cell in columns 0..lastCol exists and has borders (sundayStyle for sundays)
            for (int c = 0; c <= lastCol; c++) {
                Cell cc = row.getCell(c);
                if (cc == null) cc = row.createCell(c);
                if (sundayColumns.contains(c)) cc.setCellStyle(sundayStyle);
                else cc.setCellStyle(dataStyle);
            }

            writtenRows++;
            rowNum++;
        }

        log.info("Wrote {} rows into sheet {} (fixed layout)", writtenRows, sheet.getSheetName());
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
            default -> "";
        };
    }

}
