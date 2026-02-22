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
    private Long id;
    private Long companyId;
    private LocalDateTime createdAt =  LocalDateTime.now();
    private String invoiceNumber;
    private String status;
    private BigDecimal totalAmount;
    private LocalDateTime updatedAt;
}
