package com.example.attendance.service;

import com.example.attendance.entities.Teacher;
import com.example.attendance.entities.TimeSlot;
import com.example.attendance.entities.AppUser;
import com.example.attendance.enums.Shift;
import com.example.attendance.repository.TeacherRepository;
import com.example.attendance.repository.TimeSlotRepository;
import com.example.attendance.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TeacherService {

    private final Logger log = LoggerFactory.getLogger(TeacherService.class);

    private final TeacherRepository teacherRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final AppUserRepository appUserRepository;

    @PersistenceContext
    private final EntityManager entityManager;

    /**
     * Создаёт Teacher, получая управляемый AppUser по id.
     * Используем EntityManager.persist чтобы гарантировать корректное INSERT с @MapsId.
     */
    @Transactional
    public Teacher createTeacherForUserById(Long userId, String firstName, String lastName, String phone, Shift shift) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }

        // Получаем реальный управляемый AppUser (не proxy без инициализированного id)
        AppUser managedUser = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("AppUser not found for id=" + userId));

        Teacher t = Teacher.builder()
                .firstName(firstName)
                .lastName(lastName)
                .phone(phone)
                .shift(shift)
                .user(managedUser)
                .build();


        // Не устанавливаем t.setUserId(...) вручную — @MapsId позаботится об этом при persist.
        // Используем EntityManager.persist чтобы Hibernate выполнял INSERT для новой сущности.
        entityManager.persist(t);
        entityManager.flush(); // force INSERT now to catch errors immediately


        // Создаём таймслоты, если нужно
        if (t.getUserId() != null) {
            List<TimeSlot> existing = timeSlotRepository.findByTeacherIdOrderByStartTime(t.getUserId());
            if (existing == null || existing.isEmpty()) {
                createDefaultTimeSlotsForTeacher(t.getUserId(), shift);
            } else {
                log.info("Default timeslots already exist for teacherId={}, skipping creation", t.getUserId());
            }
        } else {
            log.warn("Persisted teacher has null userId; skipping timeslot creation");
        }

        return t;
    }

    /**
     * Существующий метод оставляем для совместимости — он делегирует к createTeacherForUserById
     */
    @Transactional
    public Teacher createTeacherForUser(AppUser user, String firstName, String lastName, String phone, Shift shift) {
        if (user == null || user.getId() == null) {
            log.warn("createTeacherForUser: provided AppUser is null or has null id");
            throw new IllegalArgumentException("AppUser must be persisted and have an id before creating Teacher");
        }
        return createTeacherForUserById(user.getId(), firstName, lastName, phone, shift);
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