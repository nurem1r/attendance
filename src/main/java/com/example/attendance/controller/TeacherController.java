package com.example.attendance.controller;

import com.example.attendance.entities.Attendance;
import com.example.attendance.entities.Payment;
import com.example.attendance.entities.Student;
import com.example.attendance.entities.Teacher;
import com.example.attendance.enums.AttendanceStatus;
import com.example.attendance.service.AttendanceService;
import com.example.attendance.service.PaymentService;
import com.example.attendance.service.StudentService;
import com.example.attendance.service.TeacherService;
import com.example.attendance.service.AppUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/teacher")
@RequiredArgsConstructor
public class TeacherController {

    private final StudentService studentService;
    private final TeacherService teacherService;
    private final AttendanceService attendanceService;
    private final AppUserService appUserService;
    private final PaymentService paymentService;

    @GetMapping
    public String dashboard(Authentication auth, Model model) {
        var principal = auth.getPrincipal();
        String username = auth.getName();
        var appUser = (com.example.attendance.entities.AppUser) appUserService.loadUserByUsername(username);
        Long userId = appUser.getId();

        Teacher teacher = teacherService.findById(userId);
        if (teacher == null) return "redirect:/login";

        List<Student> students = studentService.findByTeacherId(userId);
        Map<Long, Attendance> todays = new HashMap<>();
        LocalDate today = LocalDate.now();
        for (Student s : students) {
            attendanceService.findByStudentAndDate(s.getId(), today).ifPresent(a -> todays.put(s.getId(), a));
        }

        // missed count map and payments map
        Map<Long, Long> missedMap = new HashMap<>();
        Map<Long, List<Payment>> paymentsMap = new HashMap<>();
        for (Student s : students) {
            missedMap.put(s.getId(), attendanceService.countMissedThisMonth(s.getId()));
            paymentsMap.put(s.getId(), paymentService.findPaymentsForStudent(s.getId()));
        }

        model.addAttribute("teacher", teacher);
        model.addAttribute("students", students);
        model.addAttribute("todays", todays);
        model.addAttribute("attendanceStatuses", AttendanceStatus.values());
        model.addAttribute("missedMap", missedMap);
        model.addAttribute("paymentsMap", paymentsMap);

        return "teacher/dashboard";
    }

    @PostMapping("/save")
    public String save(@RequestParam Map<String, String> params, Authentication auth) {
        var appUser = (com.example.attendance.entities.AppUser) appUserService.loadUserByUsername(auth.getName());
        Long userId = appUser.getId();

        Map<Long, AttendanceStatus> map = params.entrySet().stream()
                .filter(e -> e.getKey().startsWith("status_"))
                .collect(Collectors.toMap(
                        e -> Long.valueOf(e.getKey().substring("status_".length())),
                        e -> AttendanceStatus.valueOf(e.getValue())
                ));

        attendanceService.saveAttendances(userId, map);

        return "redirect:/teacher";
    }
}