package com.example.attendance.repositories;

import com.example.attendance.entities.Attendance;
import com.example.attendance.enums.AttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    Optional<Attendance> findByStudentIdAndLessonDate(Long studentId, LocalDate lessonDate);
    List<Attendance> findByStudentIdAndLessonDateBetween(Long studentId, LocalDate start, LocalDate end);
    Long countByStudentIdAndStatusAndLessonDateBetween(Long studentId, AttendanceStatus status, LocalDate start, LocalDate end);
    List<Attendance> findByMarkedByUserIdAndLessonDateBetween(Long markedByUserId, LocalDate start, LocalDate end);
}