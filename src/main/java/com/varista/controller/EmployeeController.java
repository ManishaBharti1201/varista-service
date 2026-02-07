package com.varista.controller;

import com.varista.entity.Employee;
import com.varista.model.LoginRequest;
import com.varista.model.LoginResponse;
import com.varista.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/employee")
@CrossOrigin(origins = "*")
@Tag(name = "Employee Authentication", description = "Endpoints for employee login, registration, and session management")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping("/create")
    @Operation(summary = "Create a new employee", description = "Registers a new employee in the system")
    @ApiResponse(responseCode = "200", description = "Employee created successfully",
            content = @Content(schema = @Schema(implementation = LoginResponse.class)))
    public Employee createEmployee(@RequestBody Employee employeeRequest) {
        return employeeService.createEmployee(employeeRequest);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an employee", description = "Removes an employee from the system by ID")
    @ApiResponse(responseCode = "200", description = "Employee deleted successfully")
    public void deleteEmployee(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
    }

    @GetMapping("/all")
    @Operation(summary = "Get all employees", description = "Retrieves a list of all employees in the system")
    @ApiResponse(responseCode = "200", description = "List of employees retrieved successfully",
            content = @Content(schema = @Schema(implementation = Employee.class)))
    public java.util.List<Employee> getAllEmployees() {
        return employeeService.getAll();
    }

}
