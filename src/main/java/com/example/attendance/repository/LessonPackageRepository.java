package com.example.attendance.repository;

import com.example.attendance.entities.LessonPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LessonPackageRepository extends JpaRepository<LessonPackage, Long> {
    Optional<LessonPackage> findByCode(String code);

    Optional<LessonPackage> findFirstByCodeStartingWith(String prefix);
}