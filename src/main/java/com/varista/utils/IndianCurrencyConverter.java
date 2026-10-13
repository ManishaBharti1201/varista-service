package com.varista.utils;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class IndianCurrencyConverter {

    private static final String[] RUPEE_UNITS = {"", "Rupees", "Thousand", "Lakh", "Crore"};
    private static final String[] PAISA_UNITS = {"", "Paisa"};

    private static final String[] TENS = {"", "Ten", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};
    private static final String[] ONES = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};

    public static String convertRupeesToWords(BigDecimal amount) {
        if (amount == null) {
            return "";
        }

        BigDecimal roundedAmount = amount.setScale(2, RoundingMode.HALF_UP);
        if (roundedAmount.compareTo(BigDecimal.ZERO) == 0) {
            return "Zero Rupees Only";
        }

        BigDecimal wholePart = roundedAmount.setScale(0, RoundingMode.DOWN);
        BigDecimal decimalPart = roundedAmount.remainder(BigDecimal.ONE).movePointRight(2);

        StringBuilder result = new StringBuilder();

        // Handle whole part
        if (wholePart.compareTo(BigDecimal.ZERO) == 0) {
            result.append("Zero");
        } else {
            result.append(convertWholeNumberToIndianWords(wholePart));
        }
        result.append(" Rupees");

        // Handle decimal part
        if (decimalPart.compareTo(BigDecimal.ZERO) > 0) {
            result.append(" and ");
            result.append(convertWholeNumberToIndianWords(decimalPart));
            result.append(" Paisa");
        }

        result.append(" Only");

        return result.toString().trim();
    }

    private static String convertWholeNumberToIndianWords(BigDecimal number) {
        if (number.compareTo(BigDecimal.ZERO) == 0) {
            return "";
        }

        String numStr = number.toString();
        StringBuilder words = new StringBuilder();

        // Process crores
        if (numStr.length() > 7) {
            int crore = Integer.parseInt(numStr.substring(0, numStr.length() - 7));
            words.append(convertNumberToWords(crore));
            words.append(" Crore ");
            numStr = numStr.substring(numStr.length() - 7);
        }

        // Process lakhs
        if (numStr.length() > 5) {
            int lakh = Integer.parseInt(numStr.substring(0, numStr.length() - 5));
            words.append(convertNumberToWords(lakh));
            words.append(" Lakh ");
            numStr = numStr.substring(numStr.length() - 5);
        }

        // Process thousands
        if (numStr.length() > 3) {
            int thousand = Integer.parseInt(numStr.substring(0, numStr.length() - 3));
            words.append(convertNumberToWords(thousand));
            words.append(" Thousand ");
            numStr = numStr.substring(numStr.length() - 3);
        }

        // Process hundreds and below
        if (!numStr.isEmpty() && Integer.parseInt(numStr) > 0) {
            int lastThree = Integer.parseInt(numStr);
            if (number.compareTo(new BigDecimal(1000)) > 0 && lastThree < 100) {
                words.append("and ");
            }
            words.append(convertNumberToWords(lastThree));
        }

        return words.toString().trim();
    }

    private static String convertNumberToWords(int number) {
        if (number == 0) {
            return "";
        }
        if (number < 20) {
            return ONES[number];
        }
        if (number < 100) {
            return TENS[number / 10] + (number % 10 != 0 ? " " + ONES[number % 10] : "");
        }
        return ONES[number / 100] + " Hundred" + (number % 100 != 0 ? " and " + convertNumberToWords(number % 100) : "");
    }
}