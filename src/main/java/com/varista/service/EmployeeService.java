package com.varista.service;

import com.varista.entity.Employee;

import com.varista.repository.EmployeeRepository;
import com.varista.utils.InvoiceUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
        if (employeeInfo.getId() == 0)
            employeeInfo.setId(InvoiceUtils.generate5DigitId());
        if (employeeInfo.getJoiningDate() == null) {
            employeeInfo.setJoiningDate(LocalDate.now());
        }
        return employeeRepository.save(employeeInfo);
    }

    public void deleteEmployee(Long id) {
        employeeRepository.deleteById(id);
    }


}
