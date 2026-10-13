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
        log.info("NotificationController initialized.");
    }

    @PostMapping("/create")
    @Operation(summary = "Create a new notification", description = "Processes notification request and generates an notification record")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Notification created successfully",
                    content = @Content(schema = @Schema(implementation = Notification.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error during creation")
    })
    public ResponseEntity<Notification> createNotification(@RequestBody Notification commands) {
        log.info("Received request to create notification for ID: {}", commands.getId());
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

        log.info("Received request to delete notification ID: {}", id);
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
        log.info("Received request to fetch all notifications from database.");
        List<Notification> notifications = notificationService.getAll();

        if (notifications.isEmpty()) {
            log.info("No notifications found in the system.");
            return ResponseEntity.noContent().build();
        }

        log.info("Successfully retrieved {} notifications.", notifications.size());
        return ResponseEntity.ok(notifications);
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark notification as read by ID", description = "Marks a specific notification as read")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Notification marked as read successfully"),
            @ApiResponse(responseCode = "404", description = "Notification not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> markNotificationAsRead(@Parameter(description = "ID of the notification to mark as read", example = "1")
                                                       @PathVariable Integer id) {
        log.info("Received request to mark notification ID: {} as read", id);
        try {
            notificationService.markAsRead(id);
            log.info("Notification ID: {} marked as read successfully", id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error marking notification ID: {} as read: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all notifications as read", description = "Marks all existing notifications as read")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "All notifications marked as read successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> markAllNotificationsAsRead() {
        log.info("Received request to mark all notifications as read");
        try {
            notificationService.markAllAsRead();
            log.info("All notifications marked as read successfully");
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error marking all notifications as read: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
