package com.varista.repository;

import com.varista.entity.Attendance;
import com.varista.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    @Query("SELECT a FROM Attendance a WHERE a.masterEmployeeId = :masterEmployeeId")
    List<Attendance> findByMasterEmployeeId(@Param("masterEmployeeId") Long masterEmployeeId);

    @Query("SELECT a FROM Attendance a WHERE a.timesheetMonth = :timesheetMonth")
    List<Attendance> findByMonth(@Param("timesheetMonth") String month);
}
