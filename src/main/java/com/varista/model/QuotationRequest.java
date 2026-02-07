package com.varista.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;


@Data
public class QuotationRequest {
    @Id
    private Long id;
    private String quotationNumber;
    private String customerName;
    private String customerEmail;
    private List<InvoiceData.Item> items;
    private Double taxAmount;
    private Double totalAmount;
    private LocalDate creationDate;
}
