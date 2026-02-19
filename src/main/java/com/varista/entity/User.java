package com.varista.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;

@Entity
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    private String firstName;
    private String lastName;
    private long orgId;
    private String orgName;
    private String role;
    private String email;
    private String phone;
    @Lob
    @Column(name = "avatar", columnDefinition = "LONGTEXT")
    private String avatar;
    private String password;
    private LocalDate createDate;
    @UpdateTimestamp
    private LocalDate lastUpdateDate;

}
