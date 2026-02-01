package com.varista.controller;

import com.varista.constant.Constant;
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
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.*;

import java.io.Console;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/invoice")
@CrossOrigin(origins = "*")
@Tag(name = "Invoice Management", description = "Endpoints for generating, downloading, and deleting invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping("/health")
    @Operation(summary = "Service health check", description = "Simple endpoint to verify if the invoice service is responsive")
    public String healthCheck() {
        return "Invoice Service is up and running!";
    }

    @PostMapping("/create")
    public ResponseEntity<InvoiceResponse> createInvoice(@RequestBody InvoiceRequest commands) {
        try {
            InvoiceResponse response = invoiceService.createInvoice(commands);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an invoice by ID", description = "Permanently removes an invoice record from the database")
    @ApiResponse(responseCode = "204", description = "Invoice deleted successfully")
    public ResponseEntity<Void> deleteInvoice(
            @Parameter(description = "ID of the invoice to delete", example = "101")
            @PathVariable Long id) {
        invoiceService.deleteInvoice(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/{id}/download", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(
            summary = "Download invoice PDF",
            description = "Generates and streams the invoice PDF file for download",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "PDF file returned successfully",
                            content = @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary"))
                    ),
                    @ApiResponse(responseCode = "404", description = "Invoice ID not found")
            }
    )
    public ResponseEntity<Resource> downloadInvoice(
            @Parameter(description = "ID of the invoice to download", example = "101")
            @PathVariable String id) throws IOException {
        log.info("Downloading invoice with ID: {}", id);
        String clientId = id.split("-")[1] +"/";
        Path path = Paths.get(Constant.INVOICE_DIRECTORY +clientId+ id+".pdf");
        if (!Files.exists(path)) {
            log.info("Invoice file not found for ID: {}", id);
            return ResponseEntity.notFound()
                    .build();
        }
        ByteArrayResource resource = new ByteArrayResource(Files.readAllBytes(path));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
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
                                    array = @ArraySchema(
                                            schema = @Schema(implementation = InvoiceData.class)
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "204",
                            description = "No invoices found",
                            content = @Content
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Internal server error",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ErrorResponse.class)
                            )
                    )
            }
    )
    public List<InvoiceData> listInvoices() {
        return invoiceService.getAll();
    }


}
