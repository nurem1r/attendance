package com.example.attendance.service;

import com.example.attendance.entities.TimeSlot;
import com.example.attendance.repositories.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TimeSlotService {

    private final TimeSlotRepository repo;

    public List<TimeSlot> findByTeacherId(Long teacherId) {
        return repo.findByTeacherIdOrderByStartTime(teacherId);
    }
}