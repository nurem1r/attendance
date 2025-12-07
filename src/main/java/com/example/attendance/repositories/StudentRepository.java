package com.example.attendance.repositories;

import com.example.attendance.entities.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentRepository extends JpaRepository<Student, Long> {
    List<Student> findByTeacherId(Long teacherId);
    List<Student> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrStudentCodeContainingIgnoreCase(String f, String l, String code);
    Long countByTeacherId(Long teacherId);
    List<Student> findByTimeSlotId(Long timeSlotId);
}