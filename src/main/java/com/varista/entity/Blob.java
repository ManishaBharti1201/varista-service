package com.varista.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "blob_records") // Assuming a table named 'blob_records'
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Blob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String contentType; // e.g., "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    @Column(nullable = false)
    private LocalDateTime creationDate;

    @Column(nullable = false)
    private String relatedEntityName;

    @Column(nullable = false)
    private String generatedMonth;

    private String filePath;
}
