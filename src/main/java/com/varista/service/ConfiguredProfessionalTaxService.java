package com.varista.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
public class ConfiguredProfessionalTaxService implements ProfessionalTaxService {

    private final List<Slab> slabs;

    /**
     * Slabs use the format threshold:tax,threshold:tax and can be supplied per deployment/state.
     */
    public ConfiguredProfessionalTaxService(
            @Value("${payroll.professional-tax.slabs:}") String configuredSlabs) {
        if (configuredSlabs == null || configuredSlabs.isBlank()) {
            log.warn("No professional tax slabs configured; professional tax will be zero.");
            slabs = List.of();
            return;
        }

        List<Slab> configured = Arrays.stream(configuredSlabs.split(",", -1))
                .map(String::trim)
                .peek(value -> {
                    if (value.isEmpty()) {
                        throw new IllegalArgumentException("Professional tax slab configuration contains an empty entry.");
                    }
                })
                .map(ConfiguredProfessionalTaxService::parseSlab)
                .sorted(Comparator.comparing(Slab::threshold))
                .toList();
        if (configured.stream().map(Slab::threshold).distinct().count() != configured.size()) {
            throw new IllegalArgumentException("Professional tax slab thresholds must be unique.");
        }
        slabs = configured;
    }

    @Override
    public BigDecimal calculate(BigDecimal grossSalary) {
        BigDecimal gross = grossSalary == null ? BigDecimal.ZERO : grossSalary;
        return slabs.stream()
                .filter(slab -> gross.compareTo(slab.threshold()) > 0)
                .map(Slab::tax)
                .reduce((first, second) -> second)
                .orElse(BigDecimal.ZERO);
    }

    private static Slab parseSlab(String configuredSlab) {
        String[] values = configuredSlab.split(":", -1);
        if (values.length != 2) {
            throw new IllegalArgumentException(
                    "Professional tax slab must use threshold:amount format: " + configuredSlab);
        }

        BigDecimal threshold = new BigDecimal(values[0].trim());
        BigDecimal tax = new BigDecimal(values[1].trim());
        if (threshold.signum() < 0 || tax.signum() < 0) {
            throw new IllegalArgumentException("Professional tax thresholds and amounts must not be negative.");
        }
        return new Slab(threshold, tax);
    }

    private record Slab(BigDecimal threshold, BigDecimal tax) {
    }
}
