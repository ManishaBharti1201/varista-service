package com.varista.controller;

import com.varista.entity.Company;
import com.varista.model.CompanyInfo;
import com.varista.service.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/company")
@CrossOrigin(origins = "*")
@Tag(name = "Company Management", description = "Endpoints for managing and retrieving company details")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
        log.info("CompanyController initialized.");
    }

    @GetMapping("/all")
    @Operation(summary = "Get all companies", description = "Retrieves a complete list of all companies registered in the system")
    public List<Company> getCompanies() {
        log.info("Received request to get all companies.");
        return companyService.getAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get company by ID", description = "Returns a single company object based on its unique database ID")
    public Company getCompany(
            @Parameter(description = "ID of the company to retrieve", example = "1")
            @PathVariable Long id) {
        log.info("Received request to get company by ID: {}", id);
        return companyService.getCompanyById(id);
    }

    @PostMapping("/create")
    @Operation(summary = "Create a new company", description = "Adds a new company to the system")
    public Company createCompany(@RequestBody CompanyInfo company) {
        log.info("Received request to create company: {}", company);
        Company companyResponse = companyService.createCompany(company);
        return companyResponse;
    }

    @PutMapping("/updatePayment")
    public Company updatePayment(@RequestBody CompanyInfo paymentRequest){
        log.info("Received request for payment update: {}", paymentRequest);
        Company paymentResponse = companyService.updatePayment(paymentRequest);
        return paymentResponse;
    }
}
