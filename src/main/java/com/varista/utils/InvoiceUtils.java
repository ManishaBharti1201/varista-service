package com.varista.utils;

import java.math.BigDecimal;

import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

public class InvoiceUtils {

    public static String formatCurrency(BigDecimal amount) {
        return String.format("₹%.2f", amount);
    }

    public static String calculateStateTax(BigDecimal amount, BigDecimal stateTaxRate) {
        BigDecimal stateTax = amount.multiply(stateTaxRate).divide(new BigDecimal("100"));
        return formatCurrency(stateTax);
    }

    public static String calculateCentralTax(BigDecimal amount, BigDecimal centralTaxRate) {
        BigDecimal centralTax = amount.multiply(centralTaxRate).divide(new BigDecimal("100"));
        return formatCurrency(centralTax);
    }

    public static BigDecimal calculateTaxAmount(BigDecimal taxableValue, BigDecimal taxRate) {
        return taxableValue.multiply(taxRate).divide(new BigDecimal("100"));
    }

    public static synchronized String generate5DigitId() {
        final AtomicInteger sequence = new AtomicInteger(0);
        int lastMinute = -1;
        LocalTime now = LocalTime.now();
        // Calculate minutes since start of day (0 to 1439)
        int currentMinute = now.getHour() * 60 + now.getMinute();

        // Reset sequence if the minute has changed
        if (currentMinute != lastMinute) {
            sequence.set(0);
            lastMinute = currentMinute;
        }

        int seqValue = sequence.getAndIncrement();

        // If sequence exceeds 9, it will roll into the 5th digit naturally,
        // but for a strict 5-digit limit, we use modulo 10 or throw an error.
        if (seqValue > 9) {
            throw new RuntimeException("Rate limit exceeded: Max 10 IDs per minute.");
        }

        // Format: Minutes (padded to 4 digits) + Sequence (1 digit)
        return String.format("%04d%d", currentMinute, seqValue);
    }


}
