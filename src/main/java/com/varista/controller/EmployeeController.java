package com.varista.controller;

import com.varista.entity.Employee;
import com.varista.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/employee")
@CrossOrigin(origins = "*")
@Tag(name = "Employee Management", description = "Endpoints for employee lifecycle management, including registration and deletion")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping("/upsert")
    @Operation(summary = "Create a new employee", description = "Registers a new employee and persists their details in the database.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Employee created successfully",
                    content = @Content(schema = @Schema(implementation = Employee.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data provided"),
            @ApiResponse(responseCode = "409", description = "Employee with this email already exists")
    })
    public Employee createEmployee(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Employee details for registration", required = true)
            @RequestBody Employee employeeRequest) {
        return employeeService.createEmployee(employeeRequest);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an employee", description = "Removes an employee record permanently by their database ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Employee deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Employee not found with the given ID")
    })
    public void deleteEmployee(
            @Parameter(description = "The unique ID of the employee", example = "101")
            @PathVariable Long id) {
        employeeService.deleteEmployee(id);
    }

    @GetMapping("/all")
    @Operation(summary = "Get all employees", description = "Fetches a complete list of all registered employees.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of employees retrieved successfully",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = Employee.class))))
    })
    public List<Employee> getAllEmployees() {
        return employeeService.getAll();
    }
}