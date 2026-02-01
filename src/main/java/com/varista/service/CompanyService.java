package com.varista.service;

import com.varista.entity.Company;
import com.varista.model.CompanyInfo;
import com.varista.repository.CompanyRepository;
import com.varista.utils.InvoiceUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CompanyService {

    @Autowired
    private CompanyRepository companyRepository;

    public Company getCompanyById(Long id) {
        return companyRepository.findById(id).orElse(null);
    }

    public List<Company> getAll() {
        return companyRepository.findAll();
    }

    public Company createCompany(CompanyInfo companyInfo) {
        Company company = new Company();
        company.setId(InvoiceUtils.generate5DigitId());
        company.setName(companyInfo.getName());
        company.setAddress(companyInfo.getAddress());
        company.setGstin(companyInfo.getGstin());
        company.setEmail(companyInfo.getEmail());
        company.setPhone(companyInfo.getPhone());
        company.setCreatedAt(LocalDateTime.now());
        Company savedCompany = companyRepository.save(company);
        return savedCompany;
    }
}
