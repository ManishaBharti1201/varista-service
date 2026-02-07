package com.varista.entity;

import com.varista.model.InvoiceData;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Entity
@Data
public class Quotation {
    @Id
    private Long id;
    private String quotationNumber;
    private String customerName;
    private String customerEmail;
    private Double totalAmount;
    private LocalDate creationDate;
}
