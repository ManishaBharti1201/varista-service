package com.varista.controller;

import com.varista.model.InvoiceData;
import com.varista.model.InvoiceRequest;
import com.varista.model.InvoiceResponse;
import com.varista.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/invoice")
@CrossOrigin(origins = "*")
@Tag(name = "Invoice Management", description = "Endpoints for generating, downloading, and deleting invoices")
public class InvoiceController {

    @Value("${invoice.directory}")
    public String INVOICE_DIRECTORY;

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping("/health")
    @Operation(summary = "Service health check", description = "Verifies if the invoice service is responsive")
    public String healthCheck() {
        log.info("Health check initiated");
        return "Invoice Service is up and running!";
    }

    @PostMapping("/create")
    @Operation(summary = "Create a new invoice", description = "Processes invoice request and generates an invoice record")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invoice created successfully",
                    content = @Content(schema = @Schema(implementation = InvoiceResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error during creation")
    })
    public ResponseEntity<InvoiceResponse> createInvoice(@RequestBody InvoiceRequest commands) {
        log.info("Request received to create invoice for customer: {}", commands.getBuyerId());
        try {
            InvoiceResponse response = invoiceService.createInvoice(commands);
            log.info("Invoice created successfully with ID: {}", response.getInvoiceId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error occurred while creating invoice: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an invoice by ID", description = "Permanently removes an invoice record from the database")
    @ApiResponse(responseCode = "204", description = "Invoice deleted successfully")
    @ApiResponse(responseCode = "404", description = "Invoice not found")
    public ResponseEntity<Void> deleteInvoice(
            @Parameter(description = "ID of the invoice to delete", example = "101")
            @PathVariable Long id) {
        log.info("Request received to delete invoice ID: {}", id);
        try {
            invoiceService.deleteInvoice(id);
            log.info("Invoice ID: {} deleted successfully", id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            if (e.getMessage().contains("Invoice not found")) {
                log.warn("Invoice ID: {} not found for deletion", id);
                return ResponseEntity.notFound().build();
            }
            log.error("Error occurred while deleting invoice ID: {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping(value = "/{id}/download", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(
            summary = "Download invoice PDF",
            description = "Generates and streams the invoice PDF file for download based on ID format {id}-{clientId}",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "PDF file returned successfully",
                            content = @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary"))
                    ),
                    @ApiResponse(responseCode = "404", description = "Invoice file not found on disk")
            }
    )
    public ResponseEntity<Resource> downloadInvoice(
            @Parameter(description = "ID of the invoice", example = "101500")
            @PathVariable String id) {
        try {
            log.info("Processing download request for quotation ID: {}", id);
            Resource response = invoiceService.downloadInvoice(id);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + id + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(response);
        } catch (IOException e) {
            if (e.getMessage().contains("Invoice file not found")) {
                log.warn("Invoice file not found for ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .build();
            }
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/all")
    @Operation(
            summary = "Get all invoices",
            description = "Retrieves a list of all invoices stored in the system.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "List of invoices retrieved successfully",
                            content = @Content(
                                    mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = InvoiceData.class))
                            )
                    ),
                    @ApiResponse(responseCode = "204", description = "No invoices found")
            }
    )
    public ResponseEntity<List<InvoiceData>> listInvoices() {
        log.info("Fetching all invoices from database");
        List<InvoiceData> invoices = invoiceService.getAll();

        if (invoices.isEmpty()) {
            log.info("No invoices found in the system");
            return ResponseEntity.noContent().build();
        }

        log.info("Successfully retrieved {} invoices", invoices.size());
        return ResponseEntity.ok(invoices);
    }


    @GetMapping("/{id}/mark-as-paid")
    @Operation(summary = "Update an existing invoice", description = "Updates the details of an existing invoice based on the provided ID and request body")

    public ResponseEntity<InvoiceResponse> markAsPaid(
            @Parameter(description = "ID of the invoice to update", example = "101")
            @PathVariable Long id) {
        log.info("Request received to update invoice ID: {}", id);
        try {
            InvoiceResponse response = invoiceService.markAsPaid(id);
            log.info("Invoice ID: {} updated successfully", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            if (e.getMessage().contains("Invoice not found")) {
                log.warn("Invoice ID: {} not found for update", id);
                return ResponseEntity.notFound().build();
            }
            log.error("Error occurred while updating invoice ID: {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
