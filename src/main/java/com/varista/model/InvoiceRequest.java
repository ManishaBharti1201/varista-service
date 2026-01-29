package com.varista.model;

import lombok.Data;

import java.math.BigInteger;
import java.util.List;

@Data
public class InvoiceRequest {

    private String buyerId;
    private String buyerOrderNo;
    private BigInteger hsnSac;
    private double centralTaxRate;
    private double stateTaxRate;
    private double totalAmount;
    private String bankName;
    private String accountNumber;
    private String ifscCode;
    private List<ItemRequest> items;

    @Data
    public static class ItemRequest {
        private String description;
        private String hsnSac;
        private double quantity;
        private double rate;
        private String unit;
        private double amount;
    }
}