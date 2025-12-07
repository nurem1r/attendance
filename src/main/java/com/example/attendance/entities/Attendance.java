package com.example.attendance.entities;

import com.example.attendance.enums.AttendanceStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"student_id", "lesson_date"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "lesson_date")
    private LocalDate lessonDate;

    @Enumerated(EnumType.STRING)
    private AttendanceStatus status;

    private Long markedByUserId;
    private LocalDateTime markedAt;
    private LocalDateTime checkinTime;
}