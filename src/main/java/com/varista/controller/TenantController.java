package com.varista.controller;

import com.varista.tenant.TenantDatabaseProperties;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class TenantController {
    private final TenantDatabaseProperties properties;

    public TenantController(TenantDatabaseProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/tenant/all")
    public List<Map<String, String>> getTenants() {
        return properties.getTenants().entrySet().stream()
                .map(entry -> Map.of(
                        "id", String.valueOf(entry.getKey()),
                        "name", entry.getValue().getName() == null || entry.getValue().getName().isBlank()
                                ? "Company " + entry.getKey()
                                : entry.getValue().getName()))
                .toList();
    }
}
