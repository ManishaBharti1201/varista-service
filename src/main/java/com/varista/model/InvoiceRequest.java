package com.varista.model;

import lombok.Data;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

@Data
public class InvoiceRequest {
    private Long buyerId;
    private String buyerOrderNo;
    private double taxRate;
    private BigDecimal taxAmt;
    private BigDecimal taxableAmt;
    private BigDecimal totalAmt;
    private List<ItemRequest> items;
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