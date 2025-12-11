package com.example.attendance.service;

import com.example.attendance.entities.LessonPackage;
import com.example.attendance.entities.Student;
import com.example.attendance.enums.PackageType;
import com.example.attendance.repository.LessonPackageRepository;
import com.example.attendance.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final Logger log = LoggerFactory.getLogger(StudentService.class);

    private final StudentRepository studentRepository;
    private final LessonPackageRepository lessonPackageRepository;

    @Transactional
    public Student createStudent(String firstName,
                                 String lastName,
                                 String phone,
                                 PackageType packageType,
                                 Long teacherId,
                                 Long timeSlotId,
                                 Boolean book,
                                 BigDecimal debtParam,
                                 String paymentNote) {
        if (firstName == null || lastName == null) {
            throw new IllegalArgumentException("firstName/lastName required");
        }

        Student s = Student.builder()
                .firstName(firstName.trim())
                .lastName(lastName.trim())
                .phone(phone)
                .teacherId(teacherId)
                .needsBook(Boolean.TRUE.equals(book))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        try {
            s.getClass().getMethod("setTimeSlotId", Long.class).invoke(s, timeSlotId);
        } catch (NoSuchMethodException ignored) {
        } catch (Exception ex) {
            log.warn("Failed to set timeSlotId on Student via reflection: {}", ex.getMessage());
        }

        LessonPackage pkg = resolvePackageForEnum(packageType);
        if (pkg != null) {
            s.assignPackage(pkg);
            if (pkg.getLessonsCount() != null) s.setRemainingLessons(pkg.getLessonsCount());
            BigDecimal computedDebt = (debtParam != null) ? debtParam : pkg.getPrice();
            if (computedDebt == null) computedDebt = BigDecimal.ZERO;
            s.setDebt(computedDebt);

            if (paymentNote != null && !paymentNote.isBlank() && pkg.getPrice() != null) {
                BigDecimal diff = pkg.getPrice().subtract(computedDebt);
                if (diff.compareTo(BigDecimal.ZERO) > 0) {
                    s.setPaymentNote(paymentNote.trim());
                }
            }
        } else {
            s.setPackagePrice(BigDecimal.ZERO);
            s.setPackageCode(null);
            s.setDebt(debtParam == null ? BigDecimal.ZERO : debtParam);
        }

        if (s.getStudentCode() == null) {
            s.setStudentCode(generateTemporaryCode(firstName, lastName));
        }

        Student saved = studentRepository.save(s);

        if (saved.getStudentCode() != null && saved.getStudentCode().startsWith("TMP-")) {
            String real = "S" + (100000 + saved.getId());
            saved.setStudentCode(real);
            saved = studentRepository.save(saved);
        }

        log.info("Created student id={} name={} {}, package={}", saved.getId(), saved.getFirstName(), saved.getLastName(), pkg != null ? pkg.getCode() : "none");
        return saved;
    }

    @Transactional
    public Student createStudentWithPackage(String firstName,
                                            String lastName,
                                            String phone,
                                            Long packageId,
                                            Long teacherId,
                                            Long timeSlotId,
                                            Boolean book,
                                            BigDecimal initialPayment,
                                            String paymentNote) {
        if (firstName == null || lastName == null) {
            throw new IllegalArgumentException("firstName/lastName required");
        }

        LessonPackage pkg = lessonPackageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found: " + packageId));

        Student s = Student.builder()
                .firstName(firstName.trim())
                .lastName(lastName.trim())
                .phone(phone)
                .teacherId(teacherId)
                .needsBook(Boolean.TRUE.equals(book))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        try {
            s.getClass().getMethod("setTimeSlotId", Long.class).invoke(s, timeSlotId);
        } catch (NoSuchMethodException ignored) {
        } catch (Exception ex) {
            log.warn("Failed to set timeSlotId on Student via reflection: {}", ex.getMessage());
        }

        s.assignPackage(pkg);
        if (pkg.getLessonsCount() != null) s.setRemainingLessons(pkg.getLessonsCount());

        BigDecimal paid = initialPayment == null ? BigDecimal.ZERO : initialPayment;
        BigDecimal debt = pkg.getPrice().subtract(paid);
        if (debt.compareTo(BigDecimal.ZERO) < 0) debt = BigDecimal.ZERO;
        s.setDebt(debt);

        if (paymentNote != null && !paymentNote.isBlank() && paid.compareTo(BigDecimal.ZERO) > 0) {
            s.setPaymentNote(paymentNote.trim());
        }

        if (s.getStudentCode() == null) {
            s.setStudentCode(generateTemporaryCode(firstName, lastName));
        }

        Student saved = studentRepository.save(s);

        if (saved.getStudentCode() != null && saved.getStudentCode().startsWith("TMP-")) {
            String real = "S" + (100000 + saved.getId());
            saved.setStudentCode(real);
            saved = studentRepository.save(saved);
        }

        log.info("Created student id={} name={} {}, package={}", saved.getId(), saved.getFirstName(), saved.getLastName(), pkg.getCode());
        return saved;
    }

    @Transactional(readOnly = true)
    public long countByTeacherId(Long teacherId) {
        if (teacherId == null) return 0L;
        return studentRepository.countByTeacherId(teacherId);
    }

    @Transactional(readOnly = true)
    public List<Student> findAll() {
        return studentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Student> findByTimeSlotId(Long timeSlotId) {
        if (timeSlotId == null) return List.of();
        return studentRepository.findByTimeSlotId(timeSlotId);
    }

    @Transactional(readOnly = true)
    public Optional<Student> findById(Long id) {
        return studentRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Student> findByTeacherId(Long teacherId) {
        return studentRepository.findByTeacherIdOrderByLastNameAsc(teacherId);
    }

    @Transactional
    public Student updateStudent(Student student) {
        student.setUpdatedAt(Instant.now());
        return studentRepository.save(student);
    }

    @Transactional
    public BigDecimal applyPayment(Long studentId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
        Student s = studentRepository.findById(studentId).orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));
        BigDecimal currentDebt = s.getDebt() == null ? BigDecimal.ZERO : s.getDebt();
        BigDecimal newDebt = currentDebt.subtract(amount);
        if (newDebt.compareTo(BigDecimal.ZERO) < 0) newDebt = BigDecimal.ZERO;
        s.setDebt(newDebt);
        s.setUpdatedAt(Instant.now());
        studentRepository.save(s);
        log.info("Applied payment {} for student {}. New debt = {}", amount, studentId, newDebt);
        return newDebt;
    }

    @Transactional(readOnly = true)
    public List<Student> searchByNameOrCode(String q) {
        if (q == null || q.trim().isEmpty()) return findAll();
        String t = q.trim();
        return studentRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrStudentCodeContainingIgnoreCase(t, t, t);
    }

    @Transactional
    public void deleteById(Long id) {
        if (id == null) return;
        try {
            studentRepository.deleteById(id);
            log.info("Deleted student with id={}", id);
        } catch (EmptyResultDataAccessException ex) {
            log.warn("Tried to delete non-existing student id={}", id);
        }
    }

    @Transactional
    public Integer consumeLesson(Long studentId) {
        Student s = studentRepository.findById(studentId).orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));
        Integer remaining = s.getRemainingLessons();
        if (remaining == null) {
            // Not tracked (could be unlimited or missing) — nothing to consume
            return null;
        }
        if (remaining <= 0) {
            s.setRemainingLessons(0);
        } else {
            s.setRemainingLessons(remaining - 1);
        }
        s.setUpdatedAt(Instant.now());
        studentRepository.save(s);
        return s.getRemainingLessons();
    }

    private LessonPackage resolvePackageForEnum(PackageType packageType) {
        if (packageType == null) return null;

        try {
            if (packageType == PackageType.LESSONS_24) {
                return lessonPackageRepository.findByCode("LESSONS_24").orElse(null);
            } else if (packageType == PackageType.LESSONS_12) {
                return lessonPackageRepository.findByCode("LESSONS_12_MWF")
                        .or(() -> lessonPackageRepository.findByCode("LESSONS_12_TTS"))
                        .or(() -> lessonPackageRepository.findFirstByCodeStartingWith("LESSONS_12"))
                        .orElse(null);
            } else {
                // UNLIMITED or unknown
                return null;
            }
        } catch (Exception ex) {
            log.warn("Failed to resolve package for enum {}: {}", packageType, ex.getMessage());
            return null;
        }
    }

    private String generateTemporaryCode(String firstName, String lastName) {
        String f = (firstName == null || firstName.isBlank()) ? "X" : firstName.trim().toUpperCase().substring(0, 1);
        String l = (lastName == null || lastName.isBlank()) ? "X" : lastName.trim().toUpperCase().substring(0, 1);
        long t = System.currentTimeMillis() % 100000;
        return "TMP-" + f + l + "-" + t;
    }
}