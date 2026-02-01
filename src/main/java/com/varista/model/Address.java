package com.varista.model;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import lombok.Data;

@Data
@Embeddable
public class Address {
    private String addressLine1;
    private String addressLine2;
    private String cityStateZip;
}
