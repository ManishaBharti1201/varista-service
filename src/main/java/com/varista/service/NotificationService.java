package com.varista.service;

import com.varista.entity.Notification;
import com.varista.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class NotificationService {

    @Autowired
    NotificationRepository notificationRepository;

    public Notification createNotification(Notification commands) {
        log.info("Attempting to create notification: {}", commands);
        Notification savedNotification = notificationRepository.save(commands);
        log.info("Notification created successfully with ID: {}", savedNotification.getId());
        return savedNotification;
    }

    public void deleteNotification(Integer id) {
        log.info("Attempting to delete notification with ID: {}", id);
        notificationRepository.deleteById(id);
        log.info("Notification with ID: {} deleted successfully.", id);
    }

    public List<Notification> getAll() {
        log.info("Fetching all notifications.");
        List<Notification> notifications = notificationRepository.findAll();
        log.info("Retrieved {} notifications.", notifications.size());
        return notifications;
    }

    public void markAsRead(Integer id) {
        log.info("Attempting to mark notification ID: {} as read.", id);
        Optional<Notification> notificationOptional = notificationRepository.findById(id);
        if (notificationOptional.isPresent()) {
            Notification notification = notificationOptional.get();
            notification.setStatus("READ"); // Assuming a 'setRead' method exists in Notification entity
            notificationRepository.save(notification);
            log.info("Notification ID: {} marked as read successfully.", id);
        } else {
            log.warn("Notification with ID: {} not found, cannot mark as read.", id);
            throw new RuntimeException("Notification not found with ID: " + id); // Or a custom exception
        }
    }

    public void markAllAsRead() {
        log.info("Attempting to mark all notifications as read.");
        List<Notification> notifications = notificationRepository.findAll();
        notifications.forEach(notification -> notification.setStatus("READ")); // Assuming a 'setRead' method exists
        notificationRepository.saveAll(notifications);
        log.info("All {} notifications marked as read successfully.", notifications.size());
    }
}
