package com.varista.controller;

import com.varista.service.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/timesheet")
@CrossOrigin(origins = "*")
@Tag(name = "Timesheet Management", description = "Endpoints for uploading and retrieving employee attendance data")
public class AttendenceController {

    private final AttendanceService attendenceService;

    public AttendenceController(AttendanceService service) {
        attendenceService = service;
        log.info("AttendenceController initialized.");
    }

    @Operation(
            summary = "Upload Excel timesheet and generate Muster Sheet",
            description = "Parses an Excel file, links users, saves attendance records, and generates a Muster Master and Wage Sheet Excel file."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully uploaded, processed, and Excel generated",
                    content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
            @ApiResponse(responseCode = "400", description = "Invalid file or empty file provided"),
            @ApiResponse(responseCode = "500", description = "Internal server error during processing or Excel generation")
    })
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Resource> uploadTimesheet( // Changed return type to ResponseEntity<Resource>
            @Parameter(description = "Excel file containing attendance data", required = true)
            @RequestParam("file") MultipartFile file) {
        log.info("Received request to upload timesheet. File name: {}", file.getOriginalFilename());
        if (file.isEmpty()) {
            log.warn("Timesheet upload request received with empty file.");
            return ResponseEntity.badRequest().body(null); // Return null body for bad request
        }
        try {
            ByteArrayInputStream excelStream = attendenceService.uploadAttendance(file); // Service now returns ByteArrayInputStream
            log.info("Timesheet uploaded and Muster Excel generated successfully for file: {}", file.getOriginalFilename());

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Syncra_Muster_Report_" + System.currentTimeMillis() + ".xlsx");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(new InputStreamResource(excelStream));
        } catch (Exception e) {
            log.error("Timesheet upload and Muster Excel generation failed for file {}: {}", file.getOriginalFilename(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @Operation(
            summary = "Get employee timesheet",
            description = "Retrieves the summary and monthly records for a specific employee."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Found the timesheet",
                    content = { @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Object.class)) }),
            @ApiResponse(responseCode = "404", description = "Employee not found")
    })
    @GetMapping
    public ResponseEntity<?> getTimesheet(
            @Parameter(description = "The database ID of the employee", example = "764332")
            @RequestParam long employeeId){
        log.info("Received request to get timesheet for employee ID: {}", employeeId);
        var result = attendenceService.getEmployeeTimesheet(employeeId);
        log.info("Timesheet retrieved for employee ID: {}", employeeId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/register/inspect-template")
    public ResponseEntity<?> inspectTemplate() {
        log.info("Received request to inspect timesheet template headers.");
        try {
            Map<String, Object> info = attendenceService.inspectTemplateHeaders();
            log.info("Timesheet template headers inspected successfully.");
            return ResponseEntity.ok(info);
        } catch (Exception e) {
            log.error("Failed to inspect template: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Error inspecting template: " + e.getMessage());
        }
    }

    // Removed the /register/create endpoint as per the new requirement
    // @GetMapping("/register/create")
    // public ResponseEntity<?> createSheet(@RequestParam String month) {
    //     ... (logic removed)
    // }
}
