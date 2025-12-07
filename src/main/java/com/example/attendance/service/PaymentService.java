package com.example.attendance.service;

import com.example.attendance.entities.Payment;
import com.example.attendance.entities.Student;
import com.example.attendance.repositories.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final StudentService studentService;

    /**
     * Make a payment for a student: record Payment and decrease Student.debt.
     * Returns the saved Payment.
     */
    @Transactional
    public Payment makePayment(Long studentId, BigDecimal amount, Long paidByUserId, String note) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        Student student = studentService.findById(studentId);
        if (student == null) {
            throw new IllegalArgumentException("Student not found: " + studentId);
        }

        // Normalize student's debt
        if (student.getDebt() == null) {
            student.setDebt(BigDecimal.ZERO);
        }

        BigDecimal newDebt = student.getDebt().subtract(amount);
        if (newDebt.compareTo(BigDecimal.ZERO) < 0) {
            newDebt = BigDecimal.ZERO; // don't go negative
        }
        student.setDebt(newDebt);
        studentService.updateStudent(student);

        Payment payment = Payment.builder()
                .studentId(studentId)
                .amount(amount)
                .paidAt(LocalDateTime.now())
                .paidByUserId(paidByUserId)
                .note(note)
                .build();

        return paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public List<Payment> findPaymentsForStudent(Long studentId) {
        return paymentRepository.findByStudentIdOrderByPaidAtDesc(studentId);
    }
}