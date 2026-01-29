package com.varista.model;

import com.varista.entity.Company;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

@Data
public class InvoiceData {
    private String invoiceNo;
    private String dated;
    private Company seller;
    private Company buyer;
    private List<Item> items;
    private String buyerOrderNo;
    private BigDecimal totalTaxableValue;
    private BigDecimal centralTaxAmount;
    private BigDecimal igstAmount;
    private String amountInWords;
    private BankDetails bankDetails;
    private BigInteger hsnSac;
    private BigDecimal centralTaxRate;
    private BigDecimal stateTaxRate;
    private BigDecimal stateTaxAmount;
    private BigDecimal totalAmount;



    @Data
    public static class Item {
        private String description;
        private String hsnSac;
        private double quantity;
        private double rate;
        private String unit;
        private double amount;
    }

    @Data
    public static class BankDetails {
        private String bankName;
        private String accNo;
        private String ifsc;
    }
}