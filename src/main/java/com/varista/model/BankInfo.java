package com.varista.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BankInfo {

    private String bankName;
    private BigDecimal accountNumber;
    private String ifscCode;
}
