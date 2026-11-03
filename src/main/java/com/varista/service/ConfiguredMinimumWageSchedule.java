package com.varista.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

@Service
public class ConfiguredMinimumWageSchedule {

    private static final String[] REQUIRED_CATEGORIES = {"UNSKILLED", "SEMISKILLED", "SKILLED"};
    private final NavigableMap<YearMonth, Map<String, BigDecimal>> schedules = new TreeMap<>();

    public ConfiguredMinimumWageSchedule(
            @Value("${payroll.minimum-wages.schedule:}") String configuredSchedule) {
        if (configuredSchedule == null || configuredSchedule.isBlank()) {
            throw new IllegalArgumentException("Payroll minimum-wage schedules must be configured.");
        }

        Arrays.stream(configuredSchedule.split(";", -1))
                .map(String::trim)
                .forEach(this::addSchedule);
    }

    public BigDecimal minimumWageFor(String category, YearMonth payrollMonth) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Employee category is required for payroll calculation.");
        }

        String normalizedCategory = normalizeCategory(category);
        Map.Entry<YearMonth, Map<String, BigDecimal>> effectiveSchedule = schedules.floorEntry(payrollMonth);
        if (effectiveSchedule == null) {
            throw new IllegalArgumentException(
                    "No minimum-wage schedule is configured for payroll month " + payrollMonth + ".");
        }
        BigDecimal amount = effectiveSchedule.getValue().get(normalizedCategory);
        if (amount == null) {
            throw new IllegalArgumentException(
                    "No minimum wage is configured for category " + category + " effective " + effectiveSchedule.getKey() + ".");
        }
        return amount;
    }

    private void addSchedule(String entry) {
        if (entry.isEmpty()) {
            throw new IllegalArgumentException("Minimum-wage schedule contains an empty entry.");
        }

        String[] parts = entry.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "Minimum-wage schedule entries must use yyyy-MM:CATEGORY=amount,... format.");
        }

        YearMonth effectiveMonth;
        try {
            effectiveMonth = YearMonth.parse(parts[0].trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid minimum-wage schedule month: " + parts[0], exception);
        }

        Map<String, BigDecimal> amounts = new TreeMap<>();
        for (String value : parts[1].split(",", -1)) {
            String[] categoryAmount = value.trim().split("=", 2);
            if (categoryAmount.length != 2 || categoryAmount[0].isBlank()) {
                throw new IllegalArgumentException(
                        "Minimum-wage schedule amounts must use CATEGORY=amount format: " + value);
            }

            String category = normalizeCategory(categoryAmount[0]);
            BigDecimal amount;
            try {
                amount = new BigDecimal(categoryAmount[1].trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "Invalid minimum wage for category " + categoryAmount[0] + ".", exception);
            }
            if (amount.signum() <= 0 || amounts.putIfAbsent(category, amount) != null) {
                throw new IllegalArgumentException(
                        "Minimum wages must be positive with unique categories for " + effectiveMonth + ".");
            }
        }

        if (!amounts.keySet().containsAll(Arrays.asList(REQUIRED_CATEGORIES))) {
            throw new IllegalArgumentException(
                    "Every minimum-wage schedule must define Unskilled, Semiskilled, and Skilled categories.");
        }
        if (schedules.putIfAbsent(effectiveMonth, Map.copyOf(amounts)) != null) {
            throw new IllegalArgumentException("Duplicate minimum-wage schedule month: " + effectiveMonth);
        }
    }

    private static String normalizeCategory(String category) {
        return category.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
    }
}
