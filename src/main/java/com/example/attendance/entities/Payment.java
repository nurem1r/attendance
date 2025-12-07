package com.example.attendance.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FK to Student.id
    private Long studentId;

    private BigDecimal amount;

    private LocalDateTime paidAt;

    // who recorded the payment (AppUser.id)
    private Long paidByUserId;

    private String note;
}