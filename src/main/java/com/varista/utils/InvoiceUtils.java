package com.varista.utils;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.math.BigDecimal;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.util.Base64;
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

    public static synchronized Long generate5DigitId() {
        long timestamp = System.currentTimeMillis();
        // Get the last 6 digits of the timestamp
        return  (timestamp % 1000000);
    }

    public static String convertImageToBase64() {
        try {
            ClassPathResource imageResource = new ClassPathResource("quote-logo.png");
            String filePath = imageResource.getFile().getAbsolutePath();
            byte[] fileContent = Files.readAllBytes(Paths.get(filePath));
            return Base64.getEncoder().encodeToString(fileContent);
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }


}
