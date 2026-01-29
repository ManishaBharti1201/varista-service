package com.varista.model;

import lombok.Data;

@Data
public class InvoiceResponse {
    private String invoiceId;
    private String createdAt;
    private String amount;
    private String status;
    private String message;

}
