package com.example.attendance.service;

import com.example.attendance.entities.Student;
import com.example.attendance.enums.PackageType;
import com.example.attendance.repositories.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository repo;

    @Transactional
    public Student createStudent(String firstName, String lastName, String phone,
                                 PackageType packageType, Long teacherId, Long timeSlotId,
                                 Boolean book, BigDecimal debt) {
        int remaining = switch (packageType) {
            case LESSONS_12 -> 12;
            case LESSONS_24 -> 24;
            case UNLIMITED -> Integer.MAX_VALUE;
        };
        Student s = Student.builder()
                .studentCode(generateStudentCode())
                .firstName(firstName)
                .lastName(lastName)
                .phone(phone)
                .packageType(packageType)
                .usedLessons(0)
                .remainingLessons(remaining)
                .debt(debt == null ? BigDecimal.ZERO : debt)
                .book(Boolean.TRUE.equals(book))
                .teacherId(teacherId)
                .timeSlotId(timeSlotId)
                .build();
        return repo.save(s);
    }

    private String generateStudentCode() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public List<Student> findByTeacherId(Long teacherId) {
        return repo.findByTeacherId(teacherId);
    }

    public Student findById(Long id) {
        return repo.findById(id).orElse(null);
    }

    public List<Student> searchByNameOrCode(String q) {
        return repo.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrStudentCodeContainingIgnoreCase(q, q, q);
    }

    public List<Student> findAll() {
        return repo.findAll();
    }

    public Long countByTeacherId(Long teacherId) {
        return repo.countByTeacherId(teacherId);
    }

    @Transactional
    public void updateStudent(Student s) {
        repo.save(s);
    }

    public List<Student> findByTimeSlotId(Long timeSlotId) {
        return repo.findByTimeSlotId(timeSlotId);
    }

    public void deleteById(Long id) {
        repo.deleteById(id);
    }
}