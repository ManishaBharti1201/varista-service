package com.varista.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CompanyInfo {
    private Long id;
    private String name;
    private Address address;
    private String gstin;
    private String email;
    private String phone;
}
