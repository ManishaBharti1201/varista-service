package com.varista.utils;

import com.varista.constant.ApplicationConstants;
import com.varista.entity.Blob;
import com.varista.model.MusterWageInfo;
import com.varista.repository.BlobRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class ExcelUtils {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM-yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_TITLE_FORMATTER = DateTimeFormatter.ofPattern("MMMM - yyyy", Locale.ENGLISH);

    private static final int MUSTER_HEADER_ROW = 6;
    private static final int MUSTER_DATA_START_ROW = 7;
    private static final int MUSTER_DAY_START_COL = 3;
    private static final int MUSTER_TEMPLATE_DAYS = 30;
    private static final int WAGE_HEADER_ROW = 3;
    private static final int WAGE_DATA_START_ROW = 4;
    private static final int WAGE_COLUMN_COUNT = 38;

    @Autowired
    private BlobRepository blobRepository;

    public Map<String, Object> inspectTemplateHeaders() {
        Map<String, Object> result = new HashMap<>();
        result.put("headerRowIndex", -1);
        result.put("headerMap", Collections.emptyMap());
        result.put("headerNormMap", Collections.emptySet());
        Map<Integer, Integer> dayColumns = new HashMap<>();
        for (int day = 1; day <= 27; day++) {
            dayColumns.put(day, 9 + day - 1);
        }
        result.put("dayColumns", dayColumns);
        result.put("startRow", 8);
        return result;
    }

    public ByteArrayInputStream generateMasterRegisterFile(List<MusterWageInfo> attendanceData, YearMonth ym) throws IOException {
        Objects.requireNonNull(ym, "Year and month are required to generate a register.");
        List<MusterWageInfo> data = attendanceData == null ? Collections.emptyList() : attendanceData;
        log.info("Generating salary register with {} entries for {}", data.size(), ym);

        ClassPathResource resource = new ClassPathResource("salary_register_template.xlsx");
        try (InputStream input = resource.getInputStream();
             Workbook workbook = new XSSFWorkbook(input);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            while (workbook.getNumberOfSheets() > 2) {
                workbook.removeSheetAt(workbook.getNumberOfSheets() - 1);
            }
            if (workbook.getNumberOfSheets() != 2) {
                throw new IOException("Salary register template must contain muster and wage sheets.");
            }

            String month = ym.format(MONTH_FORMATTER);
            String musterSheetName = "Muster Month of " + month;
            String wageSheetName = "Wage Register " + ym.format(DateTimeFormatter.ofPattern("MMM-yyyy", Locale.ENGLISH));
            Sheet musterSheet = workbook.getSheetAt(0);
            Sheet wageSheet = workbook.getSheetAt(1);
            workbook.setSheetName(0, musterSheetName);
            workbook.setSheetName(1, wageSheetName);

            int lastMusterRow = fillMusterSheet(musterSheet, data, ym);
            int lastWageRow = fillWageSheet(wageSheet, data, ym);
            workbook.setPrintArea(0, 0, MUSTER_DAY_START_COL + ym.lengthOfMonth() + 2, 0, lastMusterRow);
            workbook.setPrintArea(1, 0, WAGE_COLUMN_COUNT - 1, 0, lastWageRow);
            workbook.setForceFormulaRecalculation(true);
            workbook.write(output);

            String subcontractor = firstNonBlank(data, MusterWageInfo::getSubContractorName, "Contractor");
            String fileName = subcontractor + "_" + ym + ".xlsx";
            try {
                Path directory = Paths.get(ApplicationConstants.MASTER_REGISTER_DIRECTORY);
                Files.createDirectories(directory);
                Path filePath = directory.resolve(fileName);
                Files.write(filePath, output.toByteArray());
                Blob blobRecord = Blob.builder()
                        .fileName(fileName)
                        .generatedMonth(ym.toString())
                        .filePath(filePath.toString())
                        .relatedEntityName(subcontractor)
                        .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .creationDate(LocalDateTime.now())
                        .build();
                blobRepository.save(blobRecord);
                log.info("Generated salary register saved to {}.", filePath.toAbsolutePath());
            } catch (Exception ex) {
                log.error("Failed to persist generated Excel file or save its blob record.", ex);
                throw new IOException("Failed to persist generated Excel file or save its blob record.", ex);
            }
            return new ByteArrayInputStream(output.toByteArray());
        }
    }

    private int fillMusterSheet(Sheet sheet, List<MusterWageInfo> data, YearMonth ym) throws IOException {
        Row header = requireRow(sheet, MUSTER_HEADER_ROW, "muster header");
        Row templateData = requireRow(sheet, MUSTER_DATA_START_ROW, "muster data");
        int dayCount = ym.lengthOfMonth();
        int summaryStart = MUSTER_DAY_START_COL + dayCount;
        int templateSummaryStart = MUSTER_DAY_START_COL + MUSTER_TEMPLATE_DAYS;
        int lastOutputColumn = summaryStart + 2;
        int lastTemplateColumn = templateSummaryStart + 2;

        CellStyle headerDayStyle = requireCell(header, MUSTER_DAY_START_COL, "muster day header").getCellStyle();
        CellStyle dataDayStyle = requireCell(templateData, MUSTER_DAY_START_COL, "muster day data").getCellStyle();
        CellStyle sundayDataStyle = requireCell(templateData, MUSTER_DAY_START_COL + 4, "muster Sunday data").getCellStyle();
        CellStyle[] summaryHeaderStyles = new CellStyle[3];
        CellStyle[] summaryDataStyles = new CellStyle[3];
        int[] summaryWidths = new int[3];
        for (int i = 0; i < 3; i++) {
            int sourceColumn = templateSummaryStart + i;
            summaryHeaderStyles[i] = requireCell(header, sourceColumn, "muster summary header").getCellStyle();
            summaryDataStyles[i] = requireCell(templateData, sourceColumn, "muster summary data").getCellStyle();
            summaryWidths[i] = sheet.getColumnWidth(sourceColumn);
        }
        int dayWidth = sheet.getColumnWidth(MUSTER_DAY_START_COL + MUSTER_TEMPLATE_DAYS - 1);

        setCellValue(sheet, 2, 12, "Name and Address of Vendor - " + firstNonBlank(data, MusterWageInfo::getSubContractorName, "Contractor"));
        setCellValue(sheet, 3, 16, projectTitle(data));
        setCellValue(sheet, 4, 15, "Month of " + ym.format(MONTH_TITLE_FORMATTER));
        setCellValue(header, 0, "SR NO");
        setCellValue(header, 1, "CSLT No");
        setCellValue(header, 2, "NAME OF EMPLOYEES");

        for (int column = MUSTER_DAY_START_COL; column <= Math.max(lastOutputColumn, lastTemplateColumn); column++) {
            setCellValue(header, column, "");
        }
        for (int day = 1; day <= dayCount; day++) {
            int column = MUSTER_DAY_START_COL + day - 1;
            setCell(header, column, day, headerDayStyle);
            if (day > MUSTER_TEMPLATE_DAYS) {
                sheet.setColumnWidth(column, dayWidth);
            }
        }
        String[] summaryHeaders = {"FRS DAYS", "W/P", "OT"};
        for (int i = 0; i < summaryHeaders.length; i++) {
            int column = summaryStart + i;
            setCell(header, column, summaryHeaders[i], summaryHeaderStyles[i]);
            sheet.setColumnWidth(column, summaryWidths[i]);
        }

        CellStyle[] fixedDataStyles = new CellStyle[3];
        for (int column = 0; column < fixedDataStyles.length; column++) {
            fixedDataStyles[column] = requireCell(templateData, column, "muster employee data").getCellStyle();
        }
        for (int index = 0; index < data.size(); index++) {
            MusterWageInfo entry = data.get(index);
            Row row = sheet.getRow(MUSTER_DATA_START_ROW + index);
            if (row == null) {
                row = sheet.createRow(MUSTER_DATA_START_ROW + index);
            }
            row.setHeight(templateData.getHeight());
            setCell(row, 0, entry.getSerialNo(), fixedDataStyles[0]);
            setCell(row, 1, entry.getEipId(), fixedDataStyles[1]);
            setCell(row, 2, entry.getEmployeeName(), fixedDataStyles[2]);

            for (int day = 1; day <= dayCount; day++) {
                int column = MUSTER_DAY_START_COL + day - 1;
                String status = entry.getDailyAttendanceStatus() == null
                        ? ""
                        : entry.getDailyAttendanceStatus().getOrDefault(day, "");
                CellStyle style = ym.atDay(day).getDayOfWeek() == DayOfWeek.SUNDAY
                        ? sundayDataStyle
                        : dataDayStyle;
                setCell(row, column, status, style);
            }

            int wpDays = countWeeklyPaidDays(entry);
            setCell(row, summaryStart, entry.getTotalDaysWorked(), summaryDataStyles[0]);
            setCell(row, summaryStart + 1, wpDays, summaryDataStyles[1]);
            setCell(row, summaryStart + 2, entry.getOverTimeHours(), summaryDataStyles[2]);
        }

        return data.isEmpty() ? MUSTER_HEADER_ROW : MUSTER_DATA_START_ROW + data.size() - 1;
    }

    private int fillWageSheet(Sheet sheet, List<MusterWageInfo> data, YearMonth ym) throws IOException {
        Row header = requireRow(sheet, WAGE_HEADER_ROW, "wage header");
        requireCell(header, WAGE_COLUMN_COUNT - 1, "wage header");
        Row templateData = requireRow(sheet, WAGE_DATA_START_ROW, "wage data");
        String vendor = firstNonBlank(data, MusterWageInfo::getSubContractorName, "Contractor");
        setCellValue(sheet, 0, 8, "WAGE REGISTER MONTH - " + ym.format(MONTH_FORMATTER).toUpperCase(Locale.ENGLISH));
        setCellValue(sheet, 1, 8, "Name and Address of Vendor - " + vendor);

        double[] totals = new double[WAGE_COLUMN_COUNT];
        for (int index = 0; index < data.size(); index++) {
            MusterWageInfo entry = data.get(index);
            Row row = sheet.getRow(WAGE_DATA_START_ROW + index);
            if (row == null) {
                row = sheet.createRow(WAGE_DATA_START_ROW + index);
            }
            row.setHeight(templateData.getHeight());
            Object[] values = wageValues(entry);
            for (int column = 0; column < WAGE_COLUMN_COUNT; column++) {
                CellStyle style = requireCell(templateData, column, "wage data").getCellStyle();
                setCell(row, column, values[column], style);
                if (column >= 8 && column != 34 && values[column] instanceof Number number) {
                    totals[column] += number.doubleValue();
                }
            }
        }

        if (data.isEmpty()) {
            return WAGE_HEADER_ROW;
        }

        int totalRowIndex = WAGE_DATA_START_ROW + data.size();
        Row totalRow = sheet.getRow(totalRowIndex);
        if (totalRow == null) {
            totalRow = sheet.createRow(totalRowIndex);
        }
        totalRow.setHeight(templateData.getHeight());
        for (int column = 0; column < WAGE_COLUMN_COUNT; column++) {
            CellStyle baseStyle = requireCell(templateData, column, "wage total").getCellStyle();
            setCell(totalRow, column, null, boldStyle(sheet.getWorkbook(), baseStyle));
        }
        setCell(totalRow, 0, "Total", boldStyle(sheet.getWorkbook(), requireCell(templateData, 0, "wage total").getCellStyle()));
        for (int column = 8; column < WAGE_COLUMN_COUNT; column++) {
            if (column != 34) {
                CellStyle baseStyle = requireCell(templateData, column, "wage total").getCellStyle();
                setCell(totalRow, column, totals[column], boldStyle(sheet.getWorkbook(), baseStyle));
            }
        }
        return totalRowIndex;
    }

    private Object[] wageValues(MusterWageInfo entry) {
        Object[] values = new Object[WAGE_COLUMN_COUNT];
        values[0] = entry.getSerialNo();
        values[1] = entry.getEipId();
        values[2] = entry.getEmployeeName();
        values[3] = entry.getGender();
        values[4] = entry.getDesignation();
        values[5] = entry.getCategory();
        values[6] = entry.getUanNo();
        values[7] = entry.getEsiNo();
        values[8] = entry.getFixedMinimumWages();
        values[9] = entry.getFixedHra();
        values[10] = entry.getFixedInterimBonus();
        values[11] = entry.getFixedWashingAllowance();
        values[12] = entry.getFixedSpecialAllowance();
        values[13] = entry.getFixedMinimumWages()
                + entry.getFixedHra()
                + entry.getFixedInterimBonus()
                + entry.getFixedWashingAllowance()
                + entry.getFixedSpecialAllowance();
        values[14] = entry.getNightHalt();
        values[15] = entry.getTotalDaysWorked();
        values[16] = entry.getOverTimeHours();
        values[17] = entry.getBasicWages();
        values[18] = entry.getHra();
        values[19] = entry.getInterimBonus();
        values[20] = entry.getWashingAllowance();
        values[21] = entry.getSpecialAllowance();
        values[22] = entry.getOtAmount();
        values[23] = entry.getNhAmount();
        values[24] = entry.getOtherAllowance();
        values[25] = entry.getGrossWage();
        values[26] = entry.getPfDeduction();
        values[27] = entry.getEsicDeduction();
        values[28] = entry.getPtDeduction();
        values[29] = entry.getLwfDeduction();
        values[30] = entry.getAdvanceRecovery();
        values[31] = entry.getOtherDeductions();
        values[32] = entry.getTotalDeductions();
        values[33] = entry.getNetWage();
        values[34] = entry.getDateOfPayment() == null ? null : Date.valueOf(entry.getDateOfPayment());
        values[35] = entry.getEmployerLwfContribution();
        values[36] = entry.getEmployerPfContribution();
        values[37] = entry.getEmployerEsicContribution();
        return values;
    }

    private static int countWeeklyPaidDays(MusterWageInfo entry) {
        if (entry.getDailyAttendanceStatus() == null) {
            return 0;
        }
        return (int) entry.getDailyAttendanceStatus().values().stream()
                .filter(Objects::nonNull)
                .map(status -> status.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", ""))
                .filter("W/P"::equals)
                .count();
    }

    private static String projectTitle(List<MusterWageInfo> data) {
        String jobCode = firstNonBlank(data, MusterWageInfo::getJobCode, "");
        return jobCode.isEmpty() ? "" : "Project - " + jobCode;
    }

    private static String firstNonBlank(List<MusterWageInfo> data,
                                        java.util.function.Function<MusterWageInfo, String> getter,
                                        String fallback) {
        return data.stream()
                .map(getter)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(fallback);
    }

    private static Row requireRow(Sheet sheet, int rowIndex, String description) throws IOException {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            throw new IOException("Salary register template is missing its " + description + " row.");
        }
        return row;
    }

    private static Cell requireCell(Row row, int columnIndex, String description) throws IOException {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            throw new IOException("Salary register template is missing a " + description + " cell.");
        }
        return cell;
    }

    private static void setCellValue(Sheet sheet, int rowIndex, int columnIndex, Object value) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        setCell(row, columnIndex, value, null);
    }

    private static void setCellValue(Row row, int columnIndex, Object value) {
        setCell(row, columnIndex, value, null);
    }

    private static void setCell(Row row, int columnIndex, Object value, CellStyle style) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            cell = row.createCell(columnIndex);
        }
        cell.setBlank();
        if (style != null) {
            cell.setCellStyle(style);
        }
        if (value == null) {
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
        } else if (value instanceof java.util.Date date) {
            cell.setCellValue(date);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private static CellStyle boldStyle(Workbook workbook, CellStyle originalStyle) {
        CellStyle style = workbook.createCellStyle();
        style.cloneStyleFrom(originalStyle);
        Font originalFont = workbook.getFontAt(originalStyle.getFontIndexAsInt());
        Font boldFont = workbook.createFont();
        boldFont.setFontName(originalFont.getFontName());
        boldFont.setFontHeightInPoints(originalFont.getFontHeightInPoints());
        boldFont.setColor(originalFont.getColor());
        boldFont.setBold(true);
        boldFont.setItalic(originalFont.getItalic());
        boldFont.setStrikeout(originalFont.getStrikeout());
        boldFont.setTypeOffset(originalFont.getTypeOffset());
        boldFont.setUnderline(originalFont.getUnderline());
        style.setFont(boldFont);
        return style;
    }

    public String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? new DataFormatter(Locale.ENGLISH).formatCellValue(cell).trim()
                    : String.valueOf((int) cell.getNumericCellValue());
            case FORMULA -> {
                try {
                    yield String.valueOf((int) cell.getNumericCellValue());
                } catch (IllegalStateException ex) {
                    yield cell.getStringCellValue().trim();
                }
            }
            default -> "";
        };
    }
}
