package com.varista.controller;

import com.varista.model.InvoiceRequest;
import com.varista.model.InvoiceResponse;
import com.varista.service.InvoiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/invoice")
@CrossOrigin(origins = "*") // Allow all origins
public class InvoiceController {

    @Autowired
    InvoiceService invoiceService;

    @GetMapping("/health")
    public String healthCheck() {
        return "Invoice Service is up and running!";
    }

    @PostMapping("/createInvoice")
    public ResponseEntity<InvoiceResponse> createInvoice(@RequestBody InvoiceRequest commands) {
        try {
            InvoiceResponse response = invoiceService.createInvoicePdfBase64(commands);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}
