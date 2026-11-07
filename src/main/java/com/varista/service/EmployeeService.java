package com.varista.service;

import com.varista.entity.Employee;

import com.varista.repository.EmployeeRepository;
import com.varista.utils.InvoiceUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
public class EmployeeService {

    @Autowired
    private EmployeeRepository employeeRepository;

    public Employee getEmployeeById(Long id) {
        log.info("Fetching employee by ID: {}", id);
        return employeeRepository.findById(id).orElse(null);
    }

    public List<Employee> getAll() {
        log.info("Fetching all employees.");
        List<Employee> employees = employeeRepository.findAll();
        log.info("Retrieved {} employees.", employees.size());
        return employees;
    }

    public Employee createEmployee(Employee employeeInfo) {
        log.info("Attempting to create/update employee with email: {}", employeeInfo.getEmail());
        if (employeeInfo.getId() == null || employeeInfo.getId() == 0) {
            employeeInfo.setId(InvoiceUtils.generate5DigitId());
            log.debug("Generated new ID {} for employee.", employeeInfo.getId());
        } else {
            employeeRepository.findById(employeeInfo.getId())
                    .ifPresent(existing -> preservePayrollProfile(employeeInfo, existing));
        }
        if (employeeInfo.getJoiningDate() == null) {
            employeeInfo.setJoiningDate(LocalDate.now(ZoneOffset.UTC));
            log.debug("Set joining date to current date for employee ID: {}", employeeInfo.getId());
        }
        Employee savedEmployee = employeeRepository.save(employeeInfo);
        log.info("Employee with ID: {} saved successfully.", savedEmployee.getId());
        return savedEmployee;
    }

    private void preservePayrollProfile(Employee updated, Employee existing) {
        if (updated.getBasicSalary() == null) updated.setBasicSalary(existing.getBasicSalary());
        if (updated.getHra() == null) updated.setHra(existing.getHra());
        if (updated.getInterimBonus() == null) updated.setInterimBonus(existing.getInterimBonus());
        if (updated.getWashingAllowance() == null) updated.setWashingAllowance(existing.getWashingAllowance());
        if (updated.getSpecialAllowance() == null) updated.setSpecialAllowance(existing.getSpecialAllowance());
        if (updated.getOtherAllowance() == null) updated.setOtherAllowance(existing.getOtherAllowance());
        if (updated.getLwfDeduction() == null) updated.setLwfDeduction(existing.getLwfDeduction());
        if (updated.getAdvanceRecovery() == null) updated.setAdvanceRecovery(existing.getAdvanceRecovery());
        if (updated.getOtherDeductions() == null) updated.setOtherDeductions(existing.getOtherDeductions());
        if (updated.getEmployerLwfContribution() == null) {
            updated.setEmployerLwfContribution(existing.getEmployerLwfContribution());
        }
        if (updated.getDateOfPayment() == null) updated.setDateOfPayment(existing.getDateOfPayment());
    }

    public void deleteEmployee(Long id) {
        log.info("Attempting to delete employee with ID: {}", id);
        employeeRepository.deleteById(id);
        log.info("Employee with ID: {} deleted successfully.", id);
    }
}
