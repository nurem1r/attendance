package com.example.attendance.entities;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Student entity adapted to reference LessonPackage.
 * - Uses Lombok for boilerplate reduction.
 * - Keeps teacherId as a simple Long (matches existing code that used s.teacherId).
 * - Stores package reference (many-to-one) and a captured packagePrice at time of assignment.
 * - Stores debt, remainingLessons and some audit timestamps.
 */
@Entity
@Table(name = "students", indexes = {
        @Index(name = "idx_student_teacher", columnList = "teacher_id"),
        @Index(name = "idx_student_code", columnList = "student_code")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // personal
    @Column(name = "first_name", nullable = false, length = 120)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 120)
    private String lastName;

    @Column(name = "phone", length = 40)
    private String phone;

    // external/visible student code (human-facing)
    @Column(name = "student_code", length = 64, unique = true)
    private String studentCode;

    // reference to teacher (stores teacher.userId as before)
    @Column(name = "teacher_id")
    private Long teacherId;

    // link to lesson package entity (nullable)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_id")
    private LessonPackage lessonPackage;

    // capture package code redundantly for easy querying/compatibility (optional)
    @Column(name = "package_code", length = 64)
    private String packageCode;

    // price of package at time of assignment (store to avoid price changes affecting historical students)
    @Column(name = "package_price", precision = 10, scale = 2)
    private BigDecimal packagePrice;

    // remaining lessons count (nullable for unlimited types)
    @Column(name = "remaining_lessons")
    private Integer remainingLessons;

    // outstanding debt (packagePrice - payments)
    @Column(name = "debt", precision = 10, scale = 2)
    private BigDecimal debt;

    // simple flag if student needs a book
    @Column(name = "needs_book")
    private Boolean needsBook = false;

    // audit
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    // helper to set package and capture price/code at once
    public void assignPackage(LessonPackage pkg) {
        if (pkg == null) {
            this.lessonPackage = null;
            this.packageCode = null;
            this.packagePrice = null;
            return;
        }
        this.lessonPackage = pkg;
        this.packageCode = pkg.getCode();
        this.packagePrice = pkg.getPrice();
    }
}