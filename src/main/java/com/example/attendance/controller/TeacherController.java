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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.math.BigDecimal;

@Controller
@RequestMapping("/teacher")
@RequiredArgsConstructor
public class TeacherController {

    private final StudentService studentService;
    private final TeacherService teacherService;
    private final AttendanceService attendanceService;
    private final AppUserService appUserService;
    private final PaymentService paymentService;

    /**
     * Dashboard: now accepts optional date request parameter.
     * If ?date=YYYY-MM-DD is provided, it will be used instead of LocalDate.now().
     */
    @GetMapping
    public String dashboard(Authentication auth,
                            @RequestParam(name = "date", required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            Model model) {
        String username = auth.getName();
        var appUser = (com.example.attendance.entities.AppUser) appUserService.loadUserByUsername(username);
        Long userId = appUser.getId();

        Teacher teacher = teacherService.findById(userId);
        if (teacher == null) return "redirect:/login";

        List<Student> students = studentService.findByTeacherId(userId);
        if (students == null) students = Collections.emptyList();

        // use provided date if present, otherwise today
        LocalDate today = (date != null) ? date : LocalDate.now();

        // today's attendance map (studentId -> Attendance)
        Map<Long, Attendance> todays = new HashMap<>();
        for (Student s : students) {
            if (s != null && s.getId() != null) {
                attendanceService.findByStudentAndDate(s.getId(), today).ifPresent(a -> todays.put(s.getId(), a));
            }
        }

        // missed count map and payments map
        Map<Long, Long> missedMap = new HashMap<>();
        Map<Long, List<Payment>> paymentsMap = new HashMap<>();
        for (Student s : students) {
            Long sid = s.getId();
            missedMap.put(sid, attendanceService.countMissedThisMonth(sid));
            paymentsMap.put(sid, paymentService.findPaymentsForStudent(sid));
        }

        // compute rowClassMap for highlighting rows in template (id -> css class)
        Map<Long, String> rowClassMap = new HashMap<>();
        for (Student s : students) {
            String cls = "";
            if (s != null) {
                if (s.getDebt() != null && s.getDebt().compareTo(BigDecimal.ZERO) > 0) {
                    cls = "row-debt";
                } else if (s.getRemainingLessons() != null) {
                    if (s.getRemainingLessons() < 2) {
                        cls = "row-low";
                    } else if (s.getRemainingLessons() < 4) {
                        cls = "row-warn";
                    }
                }
                rowClassMap.put(s.getId(), cls);
            }
        }

        long presentCount = todays.values().stream().filter(a -> a != null && a.getStatus() == AttendanceStatus.PRESENT).count();
        long lateCount = todays.values().stream().filter(a -> a != null && a.getStatus() == AttendanceStatus.LATE).count();
        long absentCount = todays.values().stream().filter(a -> a != null && a.getStatus() == AttendanceStatus.ABSENT).count();

        // Put attributes into model
        model.addAttribute("teacher", teacher);
        model.addAttribute("students", students);
        model.addAttribute("todays", todays);
        model.addAttribute("attendanceStatuses", AttendanceStatus.values());
        model.addAttribute("missedMap", missedMap);
        model.addAttribute("paymentsMap", paymentsMap);

        model.addAttribute("today", today);
        model.addAttribute("rowClassMap", rowClassMap);
        model.addAttribute("presentCount", presentCount);
        model.addAttribute("lateCount", lateCount);
        model.addAttribute("absentCount", absentCount);

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