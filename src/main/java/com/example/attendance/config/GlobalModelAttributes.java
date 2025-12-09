package com.example.attendance.config;

import com.example.attendance.entities.Attendance;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Глобальные атрибуты модели для TeacherController — защитный слой:
 * гарантированно добавляет в модель безопасные/пустые значения,
 * чтобы Thymeleaf не падал при рендеринге, если контроллер чего-то не положил.
 */
@ControllerAdvice(assignableTypes = {com.example.attendance.controller.TeacherController.class})
public class GlobalModelAttributes {

    @ModelAttribute("today")
    public LocalDate today() {
        return LocalDate.now();
    }

    @ModelAttribute("todays")
    public Map<Long, Attendance> todays() {
        return new HashMap<>();
    }

    @ModelAttribute("rowClassMap")
    public Map<Long, String> rowClassMap() {
        return new HashMap<>();
    }

    @ModelAttribute("students")
    public java.util.List<?> students() {
        return Collections.emptyList();
    }

    @ModelAttribute("presentCount")
    public Long presentCount() { return 0L; }

    @ModelAttribute("lateCount")
    public Long lateCount() { return 0L; }

    @ModelAttribute("absentCount")
    public Long absentCount() { return 0L; }
}