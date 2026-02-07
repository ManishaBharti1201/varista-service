package com.varista.service;

import ch.qos.logback.classic.pattern.EpochConverter;
import com.varista.entity.Employee;

import com.varista.model.LoginRequest;
import com.varista.model.LoginResponse;
import com.varista.repository.EmployeeRepository;
import com.varista.utils.InvoiceUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class EmployeeService {

    @Autowired
    private EmployeeRepository employeeRepository;

    public Employee getEmployeeById(Long id) {
        return employeeRepository.findById(id).orElse(null);
    }

    public List<Employee> getAll() {
        return employeeRepository.findAll();
    }


    public Employee createEmployee(Employee employeeInfo) {
        employeeInfo.setId(Instant.now().getEpochSecond());
        Employee savedEmployee = employeeRepository.save(employeeInfo);
        return savedEmployee;
    }

    public void deleteEmployee(Long id) {
        employeeRepository.deleteById(id);
    }
}
