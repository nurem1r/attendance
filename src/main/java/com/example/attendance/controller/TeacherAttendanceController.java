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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TeacherAttendanceController — расширен новым batch endpoint'ом save_batch
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
            // NEW: include debt so frontend can show debt badge
            m.put("debt", s.getDebt() == null ? 0 : s.getDebt());
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
     * Save attendance batch for a given date.
     * Accepts JSON:
     * {
     *   "date":"YYYY-MM-DD",
     *   "items":[ {"studentId":1, "status":"PRESENT", "extraLessons": 1}, ... ]
     * }
     *
     * Behavior:
     * - Validates date >= attendance.minDate (config)
     * - Applies attendance statuses using AttendanceService.saveAttendancesForDate
     * - Applies extraLessons by adjusting student.remainingLessons = remainingLessons - extraLessons
     * - Returns JSON with applied results and warnings
     */
    @PostMapping("/attendance/save_batch")
    @ResponseBody
    public ResponseEntity<?> saveAttendanceBatch(@RequestBody Map<String, Object> payload, Principal principal) {
        // parse date
        String dateStr = (String) payload.get("date");
        LocalDate date;
        try {
            date = dateStr == null ? LocalDate.now() : LocalDate.parse(dateStr);
        } catch (DateTimeParseException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "invalid_date"));
        }

        // validate minDate from config
        LocalDate minDate = LocalDate.parse(minDateStr);
        if (date.isBefore(minDate)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "date_too_early", "minDate", minDate.toString()));
        }

        var appUser = appUserService.findByUsernameSafe(principal.getName());
        if (appUser == null) return ResponseEntity.status(403).body(Map.of("success", false, "error", "forbidden"));
        // find teacher
        var teacher = teacherService.findById(appUser.getId());
        if (teacher == null) return ResponseEntity.status(403).body(Map.of("success", false, "error", "forbidden"));

        // parse items
        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
        if (items == null) items = Collections.emptyList();

        // Build map for statuses to pass into AttendanceService
        Map<Long, AttendanceStatus> statusMap = new HashMap<>();
        Map<Long, Integer> extraMap = new HashMap<>();
        List<Map<String,Object>> perItemResults = new ArrayList<>();
        for (var it : items) {
            try {
                Number sidN = (Number) it.get("studentId");
                if (sidN == null) continue;
                Long sid = sidN.longValue();
                String st = (String) it.get("status");
                Number extraN = (Number) it.get("extraLessons");
                int extra = extraN == null ? 0 : extraN.intValue();

                if (st != null) {
                    try {
                        statusMap.put(sid, AttendanceStatus.valueOf(st));
                    } catch (Exception ex) {
                        // ignore invalid status
                    }
                }
                extraMap.put(sid, extra);

                perItemResults.add(Map.of("studentId", sid, "requestedStatus", st, "extraLessons", extra));
            } catch (Exception ex) {
                // skip malformed item
            }
        }

        // First: apply statuses (this will decrement/increment remainingLessons according to AttendanceService rules)
        attendanceService.saveAttendancesForDate(appUser.getId(), date, statusMap);

        // Next: apply extraLessons (subtract extraLessons from remainingLessons)
        List<Map<String,Object>> applied = new ArrayList<>();
        for (var e : extraMap.entrySet()) {
            Long sid = e.getKey();
            int extra = e.getValue();
            Optional<Student> sOpt = studentService.findById(sid);
            if (sOpt.isEmpty()) {
                applied.add(Map.of("studentId", sid, "applied", false, "error", "student_not_found"));
                continue;
            }
            Student s = sOpt.get();
            // ownership check: the student's teacherId should equal current teacher.userId
            Long teacherUserId = s.getTeacherId();
            if (teacherUserId == null || !teacherUserId.equals(teacher.getUserId())) {
                applied.add(Map.of("studentId", sid, "applied", false, "error", "not_your_student"));
                continue;
            }
            Integer rem = s.getRemainingLessons();
            if (rem == null) {
                // not tracked
                applied.add(Map.of("studentId", sid, "applied", false, "error", "remaining_not_tracked"));
                continue;
            }
            int newRem = rem - extra; // per requirement: extra decreases remaining
            s.setRemainingLessons(newRem); // allow negative
            s.setUpdatedAt(java.time.Instant.now());
            studentService.updateStudent(s);
            applied.add(Map.of("studentId", sid, "applied", true, "newRemaining", newRem));
        }

        Map<String,Object> result = new HashMap<>();
        result.put("success", true);
        result.put("applied", applied);
        return ResponseEntity.ok(result);
    }

    // existing methods (attendanceJson & consume endpoint) can remain as before...
}