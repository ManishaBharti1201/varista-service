package com.varista.service;

import com.varista.entity.Notification;
import com.varista.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    @Autowired
    NotificationRepository notificationRepository;

    public Notification createNotification(Notification commands) {
        return notificationRepository.save(commands);
    }

    public void deleteNotification(Integer id) {

        notificationRepository.deleteById(id);
    }

    public List<Notification> getAll() {
        return notificationRepository.findAll();
    }
}
