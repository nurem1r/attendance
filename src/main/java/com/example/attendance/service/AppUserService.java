package com.example.attendance.service;

import com.example.attendance.entities.AppUser;
import com.example.attendance.enums.UserRole;
import com.example.attendance.repositories.AppUserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Service
@RequiredArgsConstructor
public class AppUserService implements UserDetailsService {

    private final AppUserRepository userRepo;
    private final BCryptPasswordEncoder passwordEncoder;
    private final TeacherService teacherService;

    @PostConstruct
    @Transactional
    public void initDefaultUsers() {
        if (userRepo.findByUsername("admin").isEmpty()) {
            AppUser admin = AppUser.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin"))
                    .role(UserRole.ADMIN)
                    .build();
            userRepo.save(admin);
        }
        if (userRepo.findByUsername("manager").isEmpty()) {
            AppUser m = AppUser.builder()
                    .username("manager")
                    .password(passwordEncoder.encode("manager"))
                    .role(UserRole.MANAGER)
                    .build();
            userRepo.save(m);
        }
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepo.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("Not found"));
    }

    /**
     * Create a teacher AppUser. Throws IllegalArgumentException if username already exists.
     * Uses saveAndFlush to ensure id is generated immediately (needed for shared-pk Teacher creation).
     */
    @Transactional
    public AppUser createTeacherUser(String username, String rawPassword) {
        if (userRepo.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        AppUser u = AppUser.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .role(UserRole.TEACHER)
                .build();
        try {
            AppUser saved = userRepo.saveAndFlush(u);
            return saved;
        } catch (DataIntegrityViolationException ex) {
            // defensive: if unique constraint lost race, translate to IllegalArgumentException
            throw new IllegalArgumentException("Username already exists: " + username, ex);
        }
    }

    @Transactional(readOnly = true)
    public AppUser findByUsernameSafe(String username) {
        return userRepo.findByUsername(username).orElse(null);
    }

    @Transactional
    public AppUser createUserDirect(String username, String rawPassword, com.example.attendance.enums.UserRole role) {
        if (userRepo.findByUsername(username).isPresent()) {
            return userRepo.findByUsername(username).get();
        }
        AppUser u = AppUser.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .build();
        return userRepo.saveAndFlush(u);
    }

    @Transactional(readOnly = true)
    public AppUser findById(Long id) {
        return userRepo.findById(id).orElse(null);
    }

    /**
     * Delete AppUser by id. Used for cleanup when Teacher creation fails.
     */
    @Transactional
    public void deleteById(Long id) {
        if (id == null) return;
        userRepo.deleteById(id);
    }
}