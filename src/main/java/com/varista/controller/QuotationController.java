package com.varista.controller;

import com.varista.entity.Quotation;
import com.varista.model.QuotationRequest;
import com.varista.model.QuotationResponse;
import com.varista.service.QuotationService;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/quotation")
@CrossOrigin(origins = "*")
@Tag(name = "Quotation Management", description = "Endpoints for generating, downloading, and deleting quotations")
public class QuotationController {

    @Value("${quotation.directory}")
    public String INVOICE_DIRECTORY;

    private final QuotationService quotationService;

    public QuotationController(QuotationService quotationService) {
        this.quotationService = quotationService;
        log.info("QuotationController initialized.");
    }

    @GetMapping("/health")
    @Operation(summary = "Service health check", description = "Verifies if the quotation service is responsive")
    public String healthCheck() {
        log.info("Received request for quotation service health check.");
        return "Quotation Service is up and running!";
    }

    @PostMapping("/create")
    @Operation(summary = "Create a new quotation", description = "Processes quotation request and generates an quotation record")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Quotation created successfully",
                    content = @Content(schema = @Schema(implementation = QuotationResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error during creation")
    })
    public ResponseEntity<Quotation> createQuotation(@RequestBody QuotationRequest commands) {
        log.info("Received request to create quotation for customer: {}", commands.getCustomerName());
        try {
            Quotation response = quotationService.createQuotation(commands);
            log.info("Quotation created successfully with ID: {}", response.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error occurred while creating quotation: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an quotation by ID", description = "Permanently removes an quotation record from the database")
    @ApiResponse(responseCode = "204", description = "Quotation deleted successfully")
    @ApiResponse(responseCode = "404", description = "Quotation not found")
    public ResponseEntity<Void> deleteQuotation(@Parameter(description = "ID of the quotation to delete", example = "101")
                                                @PathVariable Long id) {

        log.info("Received request to delete quotation ID: {}", id);
        try {
            quotationService.deleteQuotation(id);
            log.info("Quotation ID: {} deleted successfully", id);
            return ResponseEntity.status(HttpStatus.OK)
                    .build();
        } catch (IOException e) {
            log.error("Error occurred while deleting quotation ID: {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

    }

    @GetMapping(value = "/{id}/download", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(
            summary = "Download quotation PDF",
            description = "Generates and streams the quotation PDF file for download based on ID format {id}-{clientId}",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "PDF file returned successfully",
                            content = @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary"))
                    ),
                    @ApiResponse(responseCode = "404", description = "Quotation file not found on disk")
            }
    )
    public ResponseEntity<Resource> downloadQuotation(@PathVariable Long id) throws IOException {
        log.info("Received request to download quotation for ID: {}", id);
        Resource response = quotationService.downloadQuotation(id);
        log.info("Quotation ID: {} downloaded successfully.", id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(response);
    }

    @GetMapping("/all")
    @Operation(
            summary = "Get all quotations",
            description = "Retrieves a list of all quotations stored in the system.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "List of quotations retrieved successfully",
                            content = @Content(
                                    mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = Quotation.class))
                            )
                    ),
                    @ApiResponse(responseCode = "204", description = "No quotations found")
            }
    )
    public ResponseEntity<List<Quotation>> listQuotations() {
        log.info("Received request to fetch all quotations from database.");
        List<Quotation> quotations = quotationService.getAll();

        if (quotations.isEmpty()) {
            log.info("No quotations found in the system.");
            return ResponseEntity.noContent().build();
        }

        log.info("Successfully retrieved {} quotations.", quotations.size());
        return ResponseEntity.ok(quotations);
    }
}
