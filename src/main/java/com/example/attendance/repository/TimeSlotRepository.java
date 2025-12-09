package com.example.attendance.repository;

import com.example.attendance.entities.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {
    List<TimeSlot> findByTeacherIdOrderByStartTime(Long teacherId);
}