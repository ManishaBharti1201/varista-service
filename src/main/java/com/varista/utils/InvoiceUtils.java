package com.varista.utils;

import java.math.BigDecimal;

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


}
