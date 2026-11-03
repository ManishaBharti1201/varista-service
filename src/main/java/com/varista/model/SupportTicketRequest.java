package com.varista.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SupportTicketRequest {
    @NotBlank
    @Size(max = 120)
    private String contactName;

    @NotBlank
    @Size(max = 120)
    private String tenantName;

    @NotBlank
    @Pattern(regexp = "^[^\\r\\n]{4,120}$", message = "Subject must be 4 to 120 characters without line breaks")
    private String subject;

    @NotBlank
    @Size(min = 10, max = 4000)
    private String description;
}
