package com.example.attendance.repository;

import com.example.attendance.entities.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * StudentRepository — extended with helper queries used from StudentService / ManagerController.
 */
@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    List<Student> findByTeacherId(Long teacherId);

    List<Student> findByTeacherIdOrderByLastNameAsc(Long teacherId);

    Optional<Student> findByStudentCode(String studentCode);

    List<Student> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrStudentCodeContainingIgnoreCase(
            String firstName, String lastName, String studentCode);

    // count convenience (Spring Data derives implementation)
    long countByTeacherId(Long teacherId);

    // find students assigned to a timeslot (if student entity contains timeSlotId column)
    List<Student> findByTimeSlotId(Long timeSlotId);
}