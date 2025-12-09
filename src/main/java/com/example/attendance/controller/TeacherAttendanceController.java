package com.example.attendance.controller;

import com.example.attendance.entities.Attendance;
import com.example.attendance.entities.Student;
import com.example.attendance.entities.Teacher;
import com.example.attendance.enums.AttendanceStatus;
import com.example.attendance.service.AttendanceService;
import com.example.attendance.service.StudentService;
import com.example.attendance.service.TeacherService;
import com.example.attendance.service.AppUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/teacher")
public class TeacherAttendanceController {

    private final TeacherService teacherService;
    private final StudentService studentService;
    private final AttendanceService attendanceService;
    private final AppUserService appUserService; // to get current AppUser id

    /**
     * JSON endpoint: returns students for the current teacher with attendance for the given date.
     * GET /teacher/attendance/json?date=YYYY-MM-DD
     */
    @GetMapping("/attendance/json")
    @ResponseBody
    public ResponseEntity<?> attendanceJson(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                            Principal principal) {
        if (date == null) date = LocalDate.now();
        // find app user and teacher
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
            m.put("remainingLessons", s.getRemainingLessons());
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
     * Save attendance for a given date.
     * Accepts parameters:
     * - date (yyyy-MM-dd)
     * - status_<studentId>=PRESENT|ABSENT|LATE
     */
    @PostMapping("/attendance/save")
    public String saveAttendanceForDate(@RequestParam Map<String, String> params,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                        Principal principal) {
        if (date == null) date = LocalDate.now();
        var appUser = appUserService.findByUsernameSafe(principal.getName());
        if (appUser == null) return "redirect:/login";

        // parse statuses
        Map<Long, AttendanceStatus> map = new HashMap<>();
        for (var e : params.entrySet()) {
            String k = e.getKey();
            if (k.startsWith("status_")) {
                try {
                    Long sid = Long.parseLong(k.substring("status_".length()));
                    AttendanceStatus st = AttendanceStatus.valueOf(e.getValue());
                    map.put(sid, st);
                } catch (Exception ignored) {
                }
            }
        }

        attendanceService.saveAttendancesForDate(appUser.getId(), date, map);
        return "redirect:/teacher";
    }

    /**
     * Consume one lesson for a student (decrement remainingLessons).
     * URL: POST /teacher/consume/{studentId}
     * Requires current principal to be teacher-owner of student.
     * Returns JSON { success: true, remaining: <int|null> }
     */
    @PostMapping("/consume/{studentId}")
    @ResponseBody
    public ResponseEntity<?> consumeLesson(@PathVariable Long studentId, Principal principal) {
        var appUser = appUserService.findByUsernameSafe(principal.getName());
        if (appUser == null) return ResponseEntity.status(403).body(Map.of("success", false, "error", "forbidden"));

        Student s = studentService.findById(studentId).orElse(null);
        if (s == null) return ResponseEntity.badRequest().body(Map.of("success", false, "error", "student_not_found"));

        // check ownership: teacherId on student must equal current user's id
        if (s.getTeacherId() == null || !s.getTeacherId().equals(appUser.getId())) {
            return ResponseEntity.status(403).body(Map.of("success", false, "error", "not_your_student"));
        }

        Integer remaining = studentService.consumeLesson(studentId); // may return null if not tracked
        return ResponseEntity.ok(Map.of("success", true, "remaining", remaining));
    }
}