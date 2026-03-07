package com.varista.controller;

import com.varista.entity.Notification;
import com.varista.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/notification")
@CrossOrigin(origins = "*")
@Tag(name = "Notification Management", description = "Endpoints for generating, downloading, and deleting notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/create")
    @Operation(summary = "Create a new notification", description = "Processes notification request and generates an notification record")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Notification created successfully",
                    content = @Content(schema = @Schema(implementation = Notification.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error during creation")
    })
    public ResponseEntity<Notification> createNotification(@RequestBody Notification commands) {
        log.info("Request received to create notification for customer: {}", commands.getId());
        try {
            Notification response = notificationService.createNotification(commands);
            log.info("Notification created successfully with ID: {}", response.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error occurred while creating notification: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an notification by ID", description = "Permanently removes an notification record from the database")
    @ApiResponse(responseCode = "204", description = "Notification deleted successfully")
    @ApiResponse(responseCode = "404", description = "Notification not found")
    public ResponseEntity<Void> deleteNotification(@Parameter(description = "ID of the notification to delete", example = "101")
                                                @PathVariable Integer id) {

        log.info("Request received to delete notification ID: {}", id);
        notificationService.deleteNotification(id);
        log.info("Notification ID: {} deleted successfully", id);
        return ResponseEntity.status(HttpStatus.OK)
                .build();

    }

    @GetMapping("/all")
    @Operation(
            summary = "Get all notifications",
            description = "Retrieves a list of all notifications stored in the system.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "List of notifications retrieved successfully",
                            content = @Content(
                                    mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = Notification.class))
                            )
                    ),
                    @ApiResponse(responseCode = "204", description = "No notifications found")
            }
    )
    public ResponseEntity<List<Notification>> listNotifications() {
        log.info("Fetching all notifications from database");
        List<Notification> notifications = notificationService.getAll();

        if (notifications.isEmpty()) {
            log.info("No notifications found in the system");
            return ResponseEntity.noContent().build();
        }

        log.info("Successfully retrieved {} notifications", notifications.size());
        return ResponseEntity.ok(notifications);
    }
}
