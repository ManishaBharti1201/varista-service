package com.varista.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CompanyInfo {
    private Long id;
    private String name;
    private Address address;
    private String gstin;
    private String email;
    private String phone;
    private BigDecimal ledgerBalance;
}
