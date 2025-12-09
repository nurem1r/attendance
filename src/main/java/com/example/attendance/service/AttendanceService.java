package com.example.attendance.service;

import com.example.attendance.entities.Attendance;
import com.example.attendance.entities.Student;
import com.example.attendance.enums.AttendanceStatus;
import com.example.attendance.repository.AttendanceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Attendance service with improved consumption logic:
 * - Only statuses PRESENT / LATE / ABSENT consume (decrement) remainingLessons.
 * - If teacher does not explicitly mark a student for the date, system creates EXCUSED (non-consuming).
 * - When attendance status changes, remainingLessons is adjusted accordingly (restored or consumed).
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final Logger log = LoggerFactory.getLogger(AttendanceService.class);

    private final AttendanceRepository attendanceRepository;
    private final StudentService studentService;

    /**
     * Legacy convenience: save attendances for "today".
     */
    @Transactional
    public void saveAttendances(Long markerUserId, Map<Long, AttendanceStatus> studentStatusMap) {
        saveAttendancesForDate(markerUserId, LocalDate.now(), studentStatusMap);
    }

    /**
     * Save attendances for the specified date. Supports marking previous days.
     *
     * Behavior:
     * - Processes statuses supplied in studentStatusMap (studentId -> status) — updates or creates Attendance records.
     * - For students of the marker (teacher) who are NOT present in studentStatusMap:
     *     - If an Attendance record already exists for that student/date -> leave it unchanged.
     *     - If no Attendance exists -> create Attendance with status EXCUSED (non-consuming).
     * - Consumption (decrement remainingLessons) happens only when an attendance transitions from non-consuming -> consuming
     *   or when creating a new attendance with a consuming status.
     *
     * @param markerUserId     id of user (teacher) performing the marking. Used to find teacher's students.
     * @param date             LocalDate of lesson (can be past)
     * @param studentStatusMap map studentId -> AttendanceStatus (can be empty)
     */
    @Transactional
    public void saveAttendancesForDate(Long markerUserId, LocalDate date, Map<Long, AttendanceStatus> studentStatusMap) {
        if (date == null) return;
        LocalDateTime now = LocalDateTime.now();

        // Normalize input map
        Map<Long, AttendanceStatus> inputMap = studentStatusMap == null ? Collections.emptyMap() : new HashMap<>(studentStatusMap);

        // First, process all explicitly provided statuses
        for (Map.Entry<Long, AttendanceStatus> entry : inputMap.entrySet()) {
            Long studentId = entry.getKey();
            AttendanceStatus newStatus = entry.getValue();
            if (studentId == null || newStatus == null) continue;

            Optional<Student> sOpt = studentService.findById(studentId);
            if (sOpt.isEmpty()) {
                log.warn("saveAttendancesForDate: student not found id={}", studentId);
                continue;
            }
            Student student = sOpt.get();

            Attendance existing = attendanceRepository.findByStudentIdAndLessonDate(studentId, date).orElse(null);

            if (existing == null) {
                // create new attendance with newStatus
                Attendance created = Attendance.builder()
                        .studentId(studentId)
                        .lessonDate(date)
                        .status(newStatus)
                        .markedByUserId(markerUserId)
                        .markedAt(now)
                        .build();
                if (consumesLesson(newStatus)) {
                    created.setCheckinTime(now);
                } else {
                    created.setCheckinTime(null);
                }
                attendanceRepository.save(created);

                // apply consumption if status consumes
                if (consumesLesson(newStatus)) {
                    decrementRemainingIfTracked(student);
                }
            } else {
                // update existing attendance: adjust student's remainingLessons when needed
                AttendanceStatus prevStatus = existing.getStatus();
                boolean prevConsumes = consumesLesson(prevStatus);
                boolean newConsumes = consumesLesson(newStatus);

                // update fields
                existing.setStatus(newStatus);
                existing.setMarkedByUserId(markerUserId);
                existing.setMarkedAt(now);
                existing.setCheckinTime(newConsumes ? now : null);
                attendanceRepository.save(existing);

                // adjust remainingLessons based on transition
                if (!prevConsumes && newConsumes) {
                    // now consuming, decrement
                    decrementRemainingIfTracked(student);
                } else if (prevConsumes && !newConsumes) {
                    // previously consumed, now non-consuming => restore 1 lesson
                    incrementRemainingIfTracked(student);
                } else {
                    // no change in consumption semantics -> do nothing
                }
            }
        }

        // Next, ensure students of this teacher that were NOT explicitly marked are created as EXCUSED (if no attendance exists)
        // Find teacher's students
        List<Student> teacherStudents = studentService.findByTeacherId(markerUserId);
        if (teacherStudents == null) teacherStudents = Collections.emptyList();

        for (Student st : teacherStudents) {
            Long sid = st.getId();
            if (inputMap.containsKey(sid)) continue; // was explicitly handled
            // if attendance exists already, skip (we don't overwrite)
            boolean exists = attendanceRepository.findByStudentIdAndLessonDate(sid, date).isPresent();
            if (exists) continue;
            // create EXCUSED attendance, non-consuming
            Attendance exc = Attendance.builder()
                    .studentId(sid)
                    .lessonDate(date)
                    .status(AttendanceStatus.EXCUSED)
                    .markedByUserId(markerUserId)
                    .markedAt(now)
                    .checkinTime(null)
                    .build();
            attendanceRepository.save(exc);
            // do NOT decrement remainingLessons for EXCUSED
        }
    }

    /**
     * Counts absences (ABSENT) for the current month.
     */
    public Long countMissedThisMonth(Long studentId) {
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        LocalDate end = LocalDate.now();
        return attendanceRepository.countByStudentIdAndStatusAndLessonDateBetween(studentId, AttendanceStatus.ABSENT, start, end);
    }

    public Optional<Attendance> findByStudentAndDate(Long studentId, LocalDate date) {
        return attendanceRepository.findByStudentIdAndLessonDate(studentId, date);
    }

    /* ---------- helpers ---------- */

    private boolean consumesLesson(AttendanceStatus status) {
        if (status == null) return false;
        return switch (status) {
            case PRESENT, LATE, ABSENT -> true;
            default -> false; // EXCUSED and other non-consuming statuses
        };
    }

    private void decrementRemainingIfTracked(Student student) {
        Integer remaining = student.getRemainingLessons();
        if (remaining == null) return; // not tracked -> do nothing
        if (remaining > 0) {
            student.setRemainingLessons(Math.max(0, remaining - 1));
        } else {
            student.setRemainingLessons(0);
        }
        student.setUpdatedAt(java.time.Instant.now());
        studentService.updateStudent(student);
    }

    private void incrementRemainingIfTracked(Student student) {
        Integer remaining = student.getRemainingLessons();
        if (remaining == null) return; // not tracked
        student.setRemainingLessons(remaining + 1);
        student.setUpdatedAt(java.time.Instant.now());
        studentService.updateStudent(student);
    }
}