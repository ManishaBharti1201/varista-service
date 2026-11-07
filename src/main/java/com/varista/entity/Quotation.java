package com.varista.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.Instant;

@Entity
@Data
public class Quotation {
    @Id
    private Long id;
    private String quotationNumber;
    private String customerName;
    private String customerEmail;
    private Double totalAmount;
    private Instant creationDate = Instant.now();
}
