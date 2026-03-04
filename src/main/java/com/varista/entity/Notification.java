package com.varista.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Notification {
    @Id
    private Long id;
    private String description;
    private String status;
    private String createDate;
}
