package com.varista.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

@Entity
@Data
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String company_Id;
    private LocalDateTime created_at;
    private String invoice_number;
    private LocalDateTime issue_date;
    private String status;
    private BigDecimal total_amount;
    private LocalDateTime updated_at;
}
