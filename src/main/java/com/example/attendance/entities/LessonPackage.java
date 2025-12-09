package com.example.attendance.entities;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * LessonPackage entity — представляет пакет занятий с кодом, названием, ценой и расписанием.
 * Реализовано через Lombok (Data + Builder).
 */
@Entity
@Table(name = "lesson_packages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonPackage implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Уникальный код пакета, например: LESSONS_12_MWF, LESSONS_12_TTS, LESSONS_6_MON_SAT, LESSONS_24
     */
    @Column(nullable = false, unique = true, length = 64)
    private String code;

    /** Отображаемое название пакета */
    @Column(nullable = false, length = 200)
    private String title;

    /** Цена пакета в сомах (или в валюте проекта) */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** Код расписания: MWF, TTS, MON_SAT, CUSTOM и т.д. */
    @Column(nullable = false, length = 32)
    private String scheduleCode;

    /** Количество занятий в пакете (опционально) */
    private Integer lessonsCount;
}