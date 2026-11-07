package com.varista.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Data
public class Invoice {
    @Id
    private Long id;
    private Long companyId;
    private Instant createdAt = Instant.now();
    private LocalDate invoiceDate;
    private String invoiceNumber;
    private String status;
    private BigDecimal totalAmount;
    private Instant updatedAt;
}
