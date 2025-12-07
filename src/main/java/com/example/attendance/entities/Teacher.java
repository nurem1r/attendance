package com.example.attendance.entities;

import com.example.attendance.enums.Shift;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "teacher")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Teacher {

    @Id
    @Column(name = "user_id")
    private Long userId;

    private String firstName;
    private String lastName;
    private String phone;

    @Enumerated(EnumType.STRING)
    private Shift shift;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private AppUser user;
}