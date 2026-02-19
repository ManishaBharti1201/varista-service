package com.varista.model;

import lombok.Data;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;

@Data
public class InvoiceRequest {
    private Long buyerId;
    private String buyerOrderNo;
    private String status;
    private double taxRate;
    private BigDecimal taxAmt;
    private BigDecimal taxableAmt;
    private BigDecimal totalAmt;
    private List<ItemRequest> items;
    private LocalDate createdAt;
    @Data
    public static class ItemRequest {
        private String description;
        private BigInteger hsnSac;
        private double quantity;
        private double rate;
        private String unit;
        private double amount;
    }
}