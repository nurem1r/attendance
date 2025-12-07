package com.example.attendance.config;

import com.example.attendance.entities.AppUser;
import com.example.attendance.enums.Shift;
import com.example.attendance.service.AppUserService;
import com.example.attendance.service.TeacherService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Safe initializer that runs after application start and creates primary users if absent.
 * Uses AppUserService and TeacherService to avoid duplicating logic.
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AppUserService appUserService;
    private final TeacherService teacherService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("DataInitializer started...");
        try {
            // admin
            if (appUserService.findByUsernameSafe("admin") == null) {
                AppUser admin = appUserService.createUserDirect("admin", "admin", com.example.attendance.enums.UserRole.ADMIN);
                log.info("Created admin user id={}", admin.getId());
            } else {
                log.info("Admin user already exists");
            }

            // manager
            if (appUserService.findByUsernameSafe("manager") == null) {
                AppUser manager = appUserService.createUserDirect("manager", "manager", com.example.attendance.enums.UserRole.MANAGER);
                log.info("Created manager user id={}", manager.getId());
            } else {
                log.info("Manager user already exists");
            }

            // teacher (sample)
            if (appUserService.findByUsernameSafe("teacher") == null) {
                AppUser teacherUser = appUserService.createTeacherUser("teacher", "teacher");
                teacherService.createTeacherForUser(teacherUser, "Default", "Teacher", "+000000000", Shift.FIRST);
                log.info("Created sample teacher user id={}", teacherUser.getId());
            } else {
                log.info("Teacher user already exists");
            }

            log.info("DataInitializer finished successfully");
        } catch (Exception ex) {
            log.error("DataInitializer failed", ex);
        }
    }
}