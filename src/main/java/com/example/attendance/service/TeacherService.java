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

        // Логируем для диагностики
        log.info("createTeacherForUserById: preparing to persist Teacher. managedUser.id={}, teacher.user={}, teacher.userId(before)={}",
                managedUser.getId(),
                t.getUser() != null ? "present" : "null",
                t.getUserId());

        entityManager.persist(t);
        entityManager.flush(); // force INSERT now to catch errors immediately

        log.info("createTeacherForUserById: persisted Teacher. teacher.userId(after)={}, teacher object={}", t.getUserId(), t);

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

        LocalTime start;
        int count;

        if (shift == Shift.FIRST) {
            // Первая смена: 08:00 - 14:00 (6 слотов)
            start = LocalTime.of(8, 0);
            count = 6;
        } else if (shift == Shift.SECOND) {
            // Вторая смена: 14:00 - 20:00 (6 слотов)
            start = LocalTime.of(14, 0);
            count = 6;
        } else if (shift == Shift.FULL) {
            // Полная смена: 08:00 - 20:00 (12 слотов)
            start = LocalTime.of(8, 0);
            count = 12;
        } else {
            // Защита на случай неизвестной смены — поведение как для первой
            start = LocalTime.of(8, 0);
            count = 6;
        }

        for (int i = 0; i < count; i++) {
            LocalTime s = start.plusHours(i);
            LocalTime e = s.plusHours(1);
            slots.add(TimeSlot.builder()
                    .teacherId(teacherId)
                    .startTime(s)
                    .endTime(e)
                    .label(String.format("%02d:%02d-%02d:%02d", s.getHour(), s.getMinute(), e.getHour(), e.getMinute()))
                    .build());
        }

        if (!slots.isEmpty()) {
            timeSlotRepository.saveAll(slots);
        }
        log.info("Created {} default timeslots for teacherId={} (shift={})", slots.size(), teacherId, shift);
    }

    public Teacher findById(Long id) {
        return teacherRepository.findById(id).orElse(null);
    }

    public List<Teacher> findAll() {
        return teacherRepository.findAll();
    }

    /**
     * Update teacher (partial/full) and save.
     */
    @Transactional
    public Teacher updateTeacher(Teacher t) {
        if (t == null || t.getUserId() == null) {
            throw new IllegalArgumentException("Teacher or teacher.userId is null");
        }
        // ensure managed instance
        Teacher managed = teacherRepository.findById(t.getUserId()).orElseThrow(() -> new IllegalArgumentException("Teacher not found: " + t.getUserId()));
        managed.setFirstName(t.getFirstName());
        managed.setLastName(t.getLastName());
        managed.setPhone(t.getPhone());
        managed.setShift(t.getShift());
        // updatedAt field if any could be set here
        return teacherRepository.save(managed);
    }

    /**
     * Delete teacher by userId (shared PK). Does not delete AppUser.
     */
    @Transactional
    public void deleteById(Long userId) {
        if (userId == null) return;
        teacherRepository.deleteById(userId);
        log.info("Deleted Teacher with userId={}", userId);
    }
}