package com.varista.controller;

import com.varista.entity.Holiday;
import com.varista.model.HolidayRequest;
import com.varista.service.HolidayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/holiday")
@CrossOrigin(origins = "*")
@Tag(name = "Holiday Management", description = "Endpoints for managing holidays")
public class HolidayController {

    private final HolidayService holidayService;

    @Autowired
    public HolidayController(HolidayService holidayService) {
        this.holidayService = holidayService;
        log.info("HolidayController initialized.");
    }

    @GetMapping("/all")
    @Operation(summary = "Get all holidays", description = "Retrieves a list of all holidays from the database")
    public ResponseEntity<List<Holiday>> getAllHolidays() {
        log.info("Received request to get all holidays.");
        List<Holiday> holidays = holidayService.getAllHolidays();
        return ResponseEntity.ok(holidays);
    }

    @PostMapping("/create")
    @Operation(summary = "Create a new holiday", description = "Adds a new holiday record to the database")
    public ResponseEntity<Holiday> createHoliday(@RequestBody HolidayRequest request) {
        log.info("Received request to create holiday: {}", request);
        Holiday newHoliday = holidayService.createHoliday(request);
        log.info("Holiday created successfully with ID: {}", newHoliday.getId());
        return new ResponseEntity<>(newHoliday, HttpStatus.CREATED);
    }

    @DeleteMapping("/delete") // Changed to @DeleteMapping
    @Operation(summary = "Delete a holiday", description = "Deletes a holiday record based on date and type")
    public ResponseEntity<Void> deleteHoliday(@RequestBody HolidayRequest request) {
        log.info("Received request to delete holiday by date {} and type {}", request.getDate(), request.getType());
        holidayService.deleteHoliday(request);
        log.info("Holiday deleted successfully for date {} and type {}", request.getDate(), request.getType());
        return new ResponseEntity<>(HttpStatus.NO_CONTENT); // 204 No Content for successful deletion
    }
}
