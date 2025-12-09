package com.example.attendance.service;

import com.example.attendance.entities.Teacher;
import com.example.attendance.entities.TimeSlot;
import com.example.attendance.entities.AppUser;
import com.example.attendance.enums.Shift;
import com.example.attendance.repository.TeacherRepository;
import com.example.attendance.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TeacherService {

    private final Logger log = LoggerFactory.getLogger(TeacherService.class);

    private final TeacherRepository teacherRepository;
    private final TimeSlotRepository timeSlotRepository;

    /**
     * Создаёт запись Teacher, привязанную к уже существующему AppUser.
     * Явно проставляем userId (shared PK) перед сохранением и предотвращаем дублирование таймслотов.
     */
    @Transactional
    public Teacher createTeacherForUser(AppUser user, String firstName, String lastName, String phone, Shift shift) {
        Teacher t = Teacher.builder()
                .firstName(firstName)
                .lastName(lastName)
                .phone(phone)
                .shift(shift)
                .user(user)
                .build();

        // Ensure shared PK set explicitly
        if (user != null && user.getId() != null) {
            t.setUserId(user.getId());
        } else {
            log.warn("createTeacherForUser: AppUser or AppUser.id is null (username={})", user == null ? "null" : user.getUsername());
        }

        Teacher saved = teacherRepository.save(t);

        // Prevent duplicate default timeslots: only create if none exist for this teacher
        if (saved.getUserId() != null) {
            List<TimeSlot> existing = timeSlotRepository.findByTeacherIdOrderByStartTime(saved.getUserId());
            if (existing == null || existing.isEmpty()) {
                createDefaultTimeSlotsForTeacher(saved.getUserId(), shift);
            } else {
                log.info("Default timeslots already exist for teacherId={}, skipping creation", saved.getUserId());
            }
        } else {
            log.warn("Saved teacher has null userId; skipping timeslot creation");
        }

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
        log.info("Created {} default timeslots for teacherId={}", slots.size(), teacherId);
    }

    public Teacher findById(Long id) {
        return teacherRepository.findById(id).orElse(null);
    }

    public List<Teacher> findAll() {
        return teacherRepository.findAll();
    }
}