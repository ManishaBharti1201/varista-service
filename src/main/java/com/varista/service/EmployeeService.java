package com.varista.service;

import com.varista.entity.Employee;

import com.varista.repository.EmployeeRepository;
import com.varista.utils.InvoiceUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

//    public Employee createEmployee(EmployeeInfo employeeInfo) {
//        Employee employee = new Employee();
//        employee.setId(InvoiceUtils.generate5DigitId());
//        employee.setFirstName(employeeInfo.ge());
//        employee.setLastName(employeeInfo.getAddress());
//        employee.setGstin(employeeInfo.getGstin());
//        employee.setEmail(employeeInfo.getEmail());
//        employee.setPhone(employeeInfo.getPhone());
//        employee.setCreatedAt(LocalDateTime.now());
//        Employee savedEmployee = employeeRepository.save(employee);
//        return savedEmployee;
//    }
}
