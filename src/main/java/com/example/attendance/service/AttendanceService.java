package com.example.attendance.service;

import com.example.attendance.entities.Attendance;
import com.example.attendance.entities.Student;
import com.example.attendance.enums.AttendanceStatus;
import com.example.attendance.enums.PackageType;
import com.example.attendance.repositories.AttendanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final StudentService studentService;

    @Transactional
    public void saveAttendances(Long markerUserId, Map<Long, AttendanceStatus> studentStatusMap) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        for (Map.Entry<Long, AttendanceStatus> e : studentStatusMap.entrySet()) {
            Long studentId = e.getKey();
            AttendanceStatus status = e.getValue();

            Student student = studentService.findById(studentId);
            if (student == null) continue;

            Attendance attendance = attendanceRepository.findByStudentIdAndLessonDate(studentId, today)
                    .orElse(Attendance.builder()
                            .studentId(studentId)
                            .lessonDate(today)
                            .build());

            attendance.setStatus(status);
            attendance.setMarkedByUserId(markerUserId);
            attendance.setMarkedAt(now);
            attendance.setCheckinTime(now);
            attendanceRepository.save(attendance);

            // decrease remainingLessons by 1 except UNLIMITED
            if (student.getPackageType() != PackageType.UNLIMITED) {
                Integer remaining = student.getRemainingLessons() == null ? 0 : student.getRemainingLessons();
                if (remaining > 0) {
                    student.setRemainingLessons(Math.max(0, remaining - 1));
                    student.setUsedLessons((student.getUsedLessons() == null ? 0 : student.getUsedLessons()) + 1);
                } else {
                    // still increment usedLessons even if remaining 0
                    student.setUsedLessons((student.getUsedLessons() == null ? 0 : student.getUsedLessons()) + 1);
                }
                studentService.updateStudent(student);
            }
        }
    }

    public Long countMissedThisMonth(Long studentId) {
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        LocalDate end = LocalDate.now();
        return attendanceRepository.countByStudentIdAndStatusAndLessonDateBetween(studentId, AttendanceStatus.ABSENT, start, end);
    }

    public Optional<Attendance> findByStudentAndDate(Long studentId, LocalDate date) {
        return attendanceRepository.findByStudentIdAndLessonDate(studentId, date);
    }
}