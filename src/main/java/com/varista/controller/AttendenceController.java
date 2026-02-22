package com.varista.controller;

import com.varista.service.AttendenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/timesheet")
@CrossOrigin(origins = "*")
@Tag(name = "Timesheet Management", description = "Endpoints for uploading and retrieving employee attendance data")
public class AttendenceController {

    private final AttendenceService attendenceService;

    public AttendenceController(AttendenceService service) {
        attendenceService = service;
    }

    @Operation(
            summary = "Upload Excel timesheet",
            description = "Parses an Excel file, links users, and saves attendance records."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully uploaded and processed"),
            @ApiResponse(responseCode = "400", description = "Invalid file or empty file provided"),
            @ApiResponse(responseCode = "500", description = "Internal server error during processing")
    })
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadTimesheet(
            @Parameter(description = "Excel file containing attendance data", required = true)
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Please upload a valid excel file.");
        }
        try {
            long count = attendenceService.uploadAttendence(file);
            return ResponseEntity.ok().body("Timesheet Uploaded for " + count + " employee successfully");
        } catch (Exception e) {
            log.error("Timesheet upload failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
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

        var result = attendenceService.getEmployeeTimesheet(employeeId);
        return ResponseEntity.ok(result);
    }
}