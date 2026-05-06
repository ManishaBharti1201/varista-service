package com.varista.service;

import com.varista.entity.Company;
import com.varista.model.CompanyInfo;
import com.varista.repository.CompanyRepository;
import com.varista.utils.InvoiceUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class CompanyService {

    @Autowired
    private CompanyRepository companyRepository;

    public Company getCompanyById(Long id) {
        log.info("Fetching company by ID: {}", id);
        return companyRepository.findById(id).orElse(null);
    }

    public List<Company> getAll() {
        log.info("Fetching all companies.");
        return companyRepository.findAll();
    }

    public Company createCompany(CompanyInfo companyInfo) {
        log.info("Creating new company with name: {}", companyInfo.getName());
        Company company = new Company();
        company.setId(InvoiceUtils.generate5DigitId());
        company.setName(companyInfo.getName());
        company.setAddress(companyInfo.getAddress());
        company.setGstin(companyInfo.getGstin());
        company.setEmail(companyInfo.getEmail());
        company.setPhone(companyInfo.getPhone());
        company.setCreatedAt(LocalDateTime.now());
        Company savedCompany = companyRepository.save(company);
        log.info("Company created successfully with ID: {}", savedCompany.getId());
        return savedCompany;
    }

    public Company updatePayment(CompanyInfo paymentRequest) {
        log.info("Updating payment for company ID: {}", paymentRequest.getId());
        // 1. Fetch the existing company or throw an exception if not found
        Company company = companyRepository.findById(paymentRequest.getId())
                .orElseThrow(() -> {
                    log.error("Company not found with ID: {} for payment update.", paymentRequest.getId());
                    return new EntityNotFoundException("Company not found with ID: " + paymentRequest.getId());
                });

        // 2. Perform the ledger balance logic
        // ledgerBalance = currentBalance - paymentAmount

        company.setLedgerBalance(paymentRequest.getLedgerBalance());

        // 3. Update other fields only if necessary (Optional)
        // Note: Usually, we don't change the ID or CreatedAt during a payment update
        company.setName(paymentRequest.getName());
        company.setAddress(paymentRequest.getAddress());
        company.setGstin(paymentRequest.getGstin());
        company.setEmail(paymentRequest.getEmail());
        company.setPhone(paymentRequest.getPhone());
        company.setCreatedAt(LocalDateTime.now()); // Use an update timestamp instead of createdAt

        // 4. Save and return
        Company updatedCompany = companyRepository.save(company);
        log.info("Payment updated successfully for company ID: {}", updatedCompany.getId());
        return updatedCompany;
    }
}
