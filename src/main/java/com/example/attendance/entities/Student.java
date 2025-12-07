package com.example.attendance.entities;

import com.example.attendance.enums.PackageType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "student")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String studentCode;

    private String firstName;
    private String lastName;
    private String phone;

    @Enumerated(EnumType.STRING)
    private PackageType packageType;

    private Integer usedLessons;
    private Integer remainingLessons;

    private BigDecimal debt;
    private Boolean book;

    // FK to Teacher.userId
    private Long teacherId;

    // FK to TimeSlot.id
    private Long timeSlotId;
}