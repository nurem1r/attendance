package com.example.attendance.service;

import com.example.attendance.entities.Teacher;
import com.example.attendance.entities.TimeSlot;
import com.example.attendance.entities.AppUser;
import com.example.attendance.enums.Shift;
import com.example.attendance.repositories.TeacherRepository;
import com.example.attendance.repositories.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TeacherService {

    private final TeacherRepository teacherRepository;
    private final TimeSlotRepository timeSlotRepository;

    @Transactional
    public Teacher createTeacherForUser(AppUser user, String firstName, String lastName, String phone, Shift shift) {
        Teacher t = Teacher.builder()
                .firstName(firstName)
                .lastName(lastName)
                .phone(phone)
                .shift(shift)
                .user(user)
                .build();
        Teacher saved = teacherRepository.save(t);

        createDefaultTimeSlotsForTeacher(saved.getUserId(), shift);

        return saved;
    }

    private void createDefaultTimeSlotsForTeacher(Long teacherId, Shift shift) {
        List<TimeSlot> slots = new ArrayList<>();
        if (shift == Shift.FIRST) {
            LocalTime start = LocalTime.of(8, 0);
            for (int i = 0; i < 6; i++) {
                LocalTime s = start.plusHours(i);
                LocalTime e = s.plusHours(1);
                slots.add(TimeSlot.builder()
                        .teacherId(teacherId)
                        .startTime(s)
                        .endTime(e)
                        .label(String.format("%02d:%02d-%02d:%02d", s.getHour(), s.getMinute(), e.getHour(), e.getMinute()))
                        .build());
            }
        } else {
            LocalTime start = LocalTime.of(14, 0);
            for (int i = 0; i < 6; i++) {
                LocalTime s = start.plusHours(i);
                LocalTime e = s.plusHours(1);
                slots.add(TimeSlot.builder()
                        .teacherId(teacherId)
                        .startTime(s)
                        .endTime(e)
                        .label(String.format("%02d:%02d-%02d:%02d", s.getHour(), s.getMinute(), e.getHour(), e.getMinute()))
                        .build());
            }
        }
        timeSlotRepository.saveAll(slots);
    }

    public Teacher findById(Long id) {
        return teacherRepository.findById(id).orElse(null);
    }

    public List<Teacher> findAll() {
        return teacherRepository.findAll();
    }
}