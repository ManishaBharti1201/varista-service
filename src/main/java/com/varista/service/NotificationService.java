package com.varista.service;

import com.varista.entity.Notification;
import com.varista.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

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
}
