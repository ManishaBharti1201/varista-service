package com.varista.repository;

import com.varista.entity.Employee;
import com.varista.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    @Query("SELECT e FROM Employee e WHERE CONCAT(e.firstName, '|', e.lastName) IN :nameKeys")
    List<Employee> findBySpecificNameKeys(@Param("nameKeys") List<String> nameKeys);
}
