package com.varista.service;

import com.varista.entity.Employee;

import com.varista.repository.EmployeeRepository;
import com.varista.utils.InvoiceUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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
        }
        if (employeeInfo.getJoiningDate() == null) {
            employeeInfo.setJoiningDate(LocalDate.now());
            log.debug("Set joining date to current date for employee ID: {}", employeeInfo.getId());
        }
        Employee savedEmployee = employeeRepository.save(employeeInfo);
        log.info("Employee with ID: {} saved successfully.", savedEmployee.getId());
        return savedEmployee;
    }

    public void deleteEmployee(Long id) {
        log.info("Attempting to delete employee with ID: {}", id);
        employeeRepository.deleteById(id);
        log.info("Employee with ID: {} deleted successfully.", id);
    }
}
