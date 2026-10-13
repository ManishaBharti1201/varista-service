package com.varista.service;

import java.math.BigDecimal;

public interface ProfessionalTaxService {
    BigDecimal calculate(BigDecimal grossSalary);
}
