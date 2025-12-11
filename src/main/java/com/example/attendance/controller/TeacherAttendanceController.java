package com.example.attendance.controller;

import com.example.attendance.entities.Attendance;
import com.example.attendance.entities.Student;
import com.example.attendance.entities.Teacher;
import com.example.attendance.service.AttendanceService;
import com.example.attendance.service.StudentService;
import com.example.attendance.service.TeacherService;
import com.example.attendance.service.AppUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.*;

/**
 * TeacherAttendanceController — исправлена обработка платежа (BigDecimal для debt)
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/teacher")
public class TeacherAttendanceController {

    private final TeacherService teacherService;
    private final StudentService studentService;
    private final AttendanceService attendanceService;
    private final AppUserService appUserService;

    @Value("${attendance.minDate:2025-12-01}")
    private String minDateStr;

    @GetMapping("/attendance/json")
    @ResponseBody
    public ResponseEntity<?> attendanceJson(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                            Principal principal) {
        if (date == null) date = LocalDate.now();

        // validate against minDate
        LocalDate minDate = LocalDate.parse(minDateStr);
        if (date.isBefore(minDate)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "date_too_early", "minDate", minDate.toString()));
        }

        var appUser = appUserService.findByUsernameSafe(principal.getName());
        if (appUser == null) return ResponseEntity.status(403).body("forbidden");
        Teacher teacher = teacherService.findById(appUser.getId());
        if (teacher == null) return ResponseEntity.status(403).body("forbidden");

        List<Student> students = studentService.findByTeacherId(teacher.getUserId());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Student s : students) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("firstName", s.getFirstName());
            m.put("lastName", s.getLastName());
            m.put("studentCode", s.getStudentCode());
            m.put("packageType", s.getPackageCode());
            m.put("lessonPackageTitle", s.getLessonPackage() != null ? s.getLessonPackage().getTitle() : null);
            m.put("remainingLessons", s.getRemainingLessons());
            m.put("needsBook", s.getNeedsBook());
            m.put("debt", s.getDebt() == null ? BigDecimal.ZERO : s.getDebt());
            Optional<Attendance> att = attendanceService.findByStudentAndDate(s.getId(), date);
            if (att.isPresent()) {
                Attendance a = att.get();
                Map<String, Object> am = new HashMap<>();
                am.put("status", a.getStatus().name());
                am.put("checkinTime", a.getCheckinTime() == null ? null : a.getCheckinTime().toString());
                m.put("attendance", am);
            } else {
                m.put("attendance", null);
            }
            out.add(m);
        }
        return ResponseEntity.ok(out);
    }

    /**
     * Return HTML fragment for student status drawer.
     * GET /teacher/student_status/{id}?date=YYYY-MM-DD
     */
    @GetMapping("/student_status/{id}")
    public String studentStatusFragment(@PathVariable("id") Long studentId,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                        Principal principal,
                                        Model model) {
        if (date == null) date = LocalDate.now();

        var appUser = appUserService.findByUsernameSafe(principal.getName());
        if (appUser == null) {
            model.addAttribute("errorMessage", "forbidden");
            return "fragments/error_fragment :: error";
        }

        Teacher teacher = teacherService.findById(appUser.getId());
        if (teacher == null) {
            model.addAttribute("errorMessage", "forbidden");
            return "fragments/error_fragment :: error";
        }

        Optional<Student> sOpt = studentService.findById(studentId);
        if (sOpt.isEmpty()) {
            model.addAttribute("errorMessage", "Студент не найден");
            return "fragments/error_fragment :: error";
        }
        Student s = sOpt.get();

        // ensure teacher owns student (security)
        if (s.getTeacherId() == null || !s.getTeacherId().equals(teacher.getUserId())) {
            model.addAttribute("errorMessage", "У вас нет доступа к этому студенту");
            return "fragments/error_fragment :: error";
        }

        model.addAttribute("student", s);
        model.addAttribute("date", date);

        Optional<Attendance> att = attendanceService.findByStudentAndDate(studentId, date);
        model.addAttribute("attendance", att.orElse(null));

        // For now, no history — leave empty list to avoid missing methods
        model.addAttribute("recentAttendance", Collections.emptyList());

        return "teacher/student_status_fragment";
    }

    /**
     * POST /teacher/student/{id}/payment
     * Body JSON: { "amount": 500, "note": "Оплата за урок" }
     *
     * Handles BigDecimal debt on Student.
     */
    @PostMapping("/student/{id}/payment")
    @ResponseBody
    public ResponseEntity<?> postStudentPayment(@PathVariable("id") Long studentId,
                                                @RequestBody Map<String, Object> payload,
                                                Principal principal) {
        try {
            var appUser = appUserService.findByUsernameSafe(principal.getName());
            if (appUser == null) return ResponseEntity.status(403).body(Map.of("success", false, "error", "forbidden"));

            Teacher teacher = teacherService.findById(appUser.getId());
            if (teacher == null) return ResponseEntity.status(403).body(Map.of("success", false, "error", "forbidden"));

            Optional<Student> sOpt = studentService.findById(studentId);
            if (sOpt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("success", false, "error", "student_not_found"));

            Student s = sOpt.get();
            // ownership check
            if (s.getTeacherId() == null || !s.getTeacherId().equals(teacher.getUserId())) {
                return ResponseEntity.status(403).body(Map.of("success", false, "error", "not_your_student"));
            }

            // parse amount into BigDecimal
            BigDecimal amount = parseBigDecimalFromObject(payload.get("amount"));
            if (amount == null) amount = BigDecimal.ZERO;

            String note = payload.get("note") == null ? "" : payload.get("note").toString();

            // current debt (BigDecimal)
            BigDecimal currentDebt = s.getDebt() == null ? BigDecimal.ZERO : s.getDebt();
            BigDecimal newDebt = currentDebt.subtract(amount);

            s.setDebt(newDebt);
            s.setUpdatedAt(java.time.Instant.now());
            studentService.updateStudent(s);

            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("newDebt", newDebt);
            resp.put("studentId", studentId);
            resp.put("appliedAmount", amount);
            resp.put("note", note);
            return ResponseEntity.ok(resp);
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of("success", false, "error", "server_error", "message", ex.getMessage()));
        }
    }

    /* ---------- helpers ---------- */

    private BigDecimal parseBigDecimalFromObject(Object o) {
        if (o == null) return null;
        try {
            if (o instanceof BigDecimal) return (BigDecimal) o;
            if (o instanceof Number) {
                // for Integer/Long/Double/etc.
                return BigDecimal.valueOf(((Number) o).doubleValue());
            }
            String s = o.toString().trim();
            if (s.isEmpty()) return null;
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }
}