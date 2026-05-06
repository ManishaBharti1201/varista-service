package com.varista.service;

import com.varista.entity.Holiday;
import com.varista.model.HolidayRequest;
import com.varista.repository.HolidayRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class HolidayService {

    @Autowired
    private HolidayRepository holidayRepository;

    public List<Holiday> getAllHolidays() {
        log.info("Fetching all holidays.");
        List<Holiday> holidays = holidayRepository.findAll();
        log.info("Retrieved {} holidays.", holidays.size());
        return holidays;
    }

    public Holiday createHoliday(HolidayRequest request) {
        log.info("Creating new holiday: {} on {}", request.getName(), request.getDate());
        Holiday holiday = new Holiday();
        holiday.setName(request.getName());
        holiday.setDate(request.getDate());
        holiday.setType(request.getType());
        Holiday savedHoliday = holidayRepository.save(holiday);
        log.info("Holiday created successfully with ID: {}", savedHoliday.getId());
        return savedHoliday;
    }

    @Transactional
    public void deleteHoliday(HolidayRequest request) {
        log.info("Deleting holiday with date: {} and type: {}", request.getDate(), request.getType());
        holidayRepository.deleteByDateAndType(request.getDate(), request.getType());
        log.info("Holiday with date: {} and type: {} deleted successfully.", request.getDate(), request.getType());
    }
}
