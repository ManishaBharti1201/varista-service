package com.varista.entity;

import com.varista.model.Address;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Generated;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;


@Entity
@Data
public class Company {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private Address address;
    private String gstin;
    private String email;
    private String phone;        // changed from BigDecimal → String
    private LocalDateTime createdAt;

}