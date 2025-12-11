package com.example.attendance.controller;

import com.example.attendance.entities.AppUser;
import com.example.attendance.entities.Student;
import com.example.attendance.entities.Teacher;
import com.example.attendance.entities.TimeSlot;
import com.example.attendance.entities.LessonPackage;
import com.example.attendance.enums.PackageType;
import com.example.attendance.repository.LessonPackageRepository;
import com.example.attendance.service.AppUserService;
import com.example.attendance.service.AttendanceService;
import com.example.attendance.service.PaymentService;
import com.example.attendance.service.StudentService;
import com.example.attendance.service.TeacherService;
import com.example.attendance.service.TimeSlotService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/manager")
@RequiredArgsConstructor
public class ManagerController {

    private final Logger log = LoggerFactory.getLogger(ManagerController.class);

    private final TeacherService teacherService;
    private final AppUserService appUserService;
    private final StudentService studentService;
    private final TimeSlotService timeSlotService;
    private final PaymentService paymentService;
    private final AttendanceService attendanceService;
    private final LessonPackageRepository lessonPackageRepository;

    @GetMapping
    public String dashboard(Model model) {
        List<Teacher> teachers = teacherService.findAll();
        Map<Long, Long> studentsCountMap = new HashMap<>();
        for (Teacher t : teachers) {
            studentsCountMap.put(t.getUserId(), studentService.countByTeacherId(t.getUserId()));
        }
        model.addAttribute("teachers", teachers);
        model.addAttribute("studentsCountMap", studentsCountMap);
        return "manager/dashboard";
    }

    @GetMapping("/add_teacher")
    public String addTeacherForm() {
        return "manager/add_teacher";
    }

    /**
     * Create teacher: check username first, create AppUser, then Teacher.
     * If Teacher creation fails after AppUser created — rollback AppUser to avoid orphans.
     */
    @PostMapping("/add_teacher")
    public String addTeacher(@RequestParam String firstName,
                             @RequestParam String lastName,
                             @RequestParam String phone,
                             @RequestParam com.example.attendance.enums.Shift shift,
                             @RequestParam String username,
                             @RequestParam String password) {
        // check username existence
        if (appUserService.findByUsernameSafe(username) != null) {
            log.warn("Attempt to create teacher with existing username={}", username);
            return "redirect:/manager?error=username_exists";
        }

        AppUser user = null;
        try {
            user = appUserService.createTeacherUser(username, password);
        } catch (IllegalArgumentException | DataIntegrityViolationException ex) {
            log.warn("Failed to create AppUser for username={}, reason={}", username, ex.getMessage());
            return "redirect:/manager?error=user_create";
        } catch (Exception ex) {
            log.error("Unexpected error creating AppUser for username={}", username, ex);
            return "redirect:/manager?error=user_create";
        }

        // create Teacher profile
        try {
            Teacher teacher = teacherService.createTeacherForUserById(user.getId(), firstName, lastName, phone, shift);
            if (teacher == null || teacher.getUserId() == null) {
                log.error("Teacher profile creation failed for username={}, userId={}", username, user.getId());
                // cleanup user to avoid orphaned AppUser without Teacher (optional)
                try {
                    appUserService.deleteById(user.getId());
                } catch (Exception e) {
                    log.error("Failed to cleanup AppUser id={} after teacher creation failure", user.getId(), e);
                }
                return "redirect:/manager?error=teacher_create";
            }
        } catch (Exception ex) {
            log.error("Failed to create Teacher for userId={}, rolling back user. Reason: {}", user.getId(), ex.getMessage());
            // cleanup created AppUser
            try {
                appUserService.deleteById(user.getId());
            } catch (Exception e) {
                log.error("Failed to cleanup AppUser id={} after exception", user.getId(), e);
            }
            return "redirect:/manager?error=teacher_create";
        }

        log.info("Created teacher username={} userId={}", username, user.getId());
        return "redirect:/manager?success=teacher_created";
    }

    @GetMapping("/add_student")
    public String addStudentForm(Model model) {
        // Pass actual LessonPackage entities to the template so UI can show friendly titles/prices.
        List<LessonPackage> packages = lessonPackageRepository.findAll();
        model.addAttribute("packages", packages);

        // Keep teachers for the select
        model.addAttribute("teachers", teacherService.findAll());
        return "manager/add_student";
    }

    @PostMapping("/add_student")
    public String addStudent(@RequestParam String firstName,
                             @RequestParam String lastName,
                             @RequestParam(required = false) String phone,
                             // try to accept packageId (preferred). Keep PackageType for backward compatibility.
                             @RequestParam(required = false) Long packageId,
                             @RequestParam(required = false) PackageType packageType,
                             @RequestParam Long teacherId,
                             @RequestParam(required = false) Long timeSlotId,
                             @RequestParam(required = false) Boolean book,
                             @RequestParam(required = false) BigDecimal initialPayment,
                             @RequestParam(required = false) BigDecimal debt) {

        // Prefer packageId (new flow). Fall back to enum-based flow for compatibility.
        if (packageId != null) {
            studentService.createStudentWithPackage(firstName, lastName, phone, packageId, teacherId, timeSlotId, book, initialPayment);
        } else {
            // legacy: manager passes enum PackageType; pass through to existing service method
            studentService.createStudent(firstName, lastName, phone, packageType, teacherId, timeSlotId, book, debt);
        }
        return "redirect:/manager/student_list";
    }

    @GetMapping("/student_list")
    public String studentList(Model model,
                              @RequestParam(required = false) String q,
                              @RequestParam(required = false) Long teacherFilter) {
        List<Student> students;
        if (q != null && !q.isBlank()) {
            students = studentService.searchByNameOrCode(q);
        } else {
            students = studentService.findAll();
        }

        // teacher map for display
        List<Teacher> teachers = teacherService.findAll();
        Map<Long, Teacher> teacherMap = teachers.stream().collect(Collectors.toMap(Teacher::getUserId, t -> t));

        // missed and payments maps
        Map<Long, Long> missedMap = new HashMap<>();
        Map<Long, List<com.example.attendance.entities.Payment>> paymentsMap = new HashMap<>();
        for (Student s : students) {
            missedMap.put(s.getId(), attendanceService.countMissedThisMonth(s.getId()));
            paymentsMap.put(s.getId(), paymentService.findPaymentsForStudent(s.getId()));
        }

        model.addAttribute("students", students);
        model.addAttribute("teachers", teachers);
        model.addAttribute("teacherMap", teacherMap);
        model.addAttribute("missedMap", missedMap);
        model.addAttribute("paymentsMap", paymentsMap);
        // expose current search query so template can keep input value
        model.addAttribute("q", q);

        return "manager/student_list";
    }

    @GetMapping("/teacher_list")
    public String teacherList(Model model) {
        List<Teacher> teachers = teacherService.findAll();
        model.addAttribute("teachers", teachers);
        return "manager/teacher_list";
    }

    @GetMapping("/teacher_slots/{teacherId}")
    public String teacherSlots(@PathVariable Long teacherId, Model model) {
        Teacher teacher = teacherService.findById(teacherId);
        List<TimeSlot> slots = timeSlotService.findByTeacherId(teacherId);
        // build map slotId -> students
        Map<Long, List<Student>> studentsBySlot = new HashMap<>();
        for (TimeSlot slot : slots) {
            studentsBySlot.put(slot.getId(), studentService.findByTimeSlotId(slot.getId()));
        }
        model.addAttribute("teacher", teacher);
        model.addAttribute("slots", slots);
        model.addAttribute("studentsBySlot", studentsBySlot);
        return "manager/teacher_slots";
    }

    /**
     * Возвращает JSON список TimeSlot (id + label) для указанного teacherId.
     * Используется клиентским JS в форме добавления студента.
     */
    @GetMapping("/teacher_slots_json/{teacherId}")
    @ResponseBody
    public List<Map<String, Object>> teacherSlotsJson(@PathVariable Long teacherId) {
        List<TimeSlot> slots = timeSlotService.findByTeacherId(teacherId);
        List<Map<String, Object>> out = new ArrayList<>();
        if (slots != null) {
            for (TimeSlot slot : slots) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", slot.getId());
                m.put("label", slot.getLabel());
                out.add(m);
            }
        }
        return out;
    }

    /* ------------------ NEW: edit / update / delete student ------------------ */

    @GetMapping("/edit_student/{id}")
    public String editStudentForm(@PathVariable Long id, Model model) {
        Optional<Student> sOpt = studentService.findById(id);
        if (sOpt.isEmpty()) {
            return "redirect:/manager/student_list?error=not_found";
        }
        Student s = sOpt.get();
        model.addAttribute("student", s);

        // packages and teachers for selects
        List<LessonPackage> packages = lessonPackageRepository.findAll();
        model.addAttribute("packages", packages);
        model.addAttribute("teachers", teacherService.findAll());

        // load timeslots for selected teacher (if any)
        Long teacherId = s.getTeacherId();
        List<TimeSlot> slots = teacherId == null ? List.of() : timeSlotService.findByTeacherId(teacherId);
        model.addAttribute("slots", slots);

        return "manager/edit_student";
    }

    @PostMapping("/edit_student")
    public String updateStudent(@RequestParam Long id,
                                @RequestParam String firstName,
                                @RequestParam String lastName,
                                @RequestParam(required = false) String phone,
                                @RequestParam(required = false) Long packageId,
                                @RequestParam(required = false) Long teacherId,
                                @RequestParam(required = false) Long timeSlotId,
                                @RequestParam(required = false) Boolean book,
                                @RequestParam(required = false) BigDecimal initialPayment,
                                @RequestParam(required = false) BigDecimal debt) {
        Optional<Student> sOpt = studentService.findById(id);
        if (sOpt.isEmpty()) {
            return "redirect:/manager/student_list?error=not_found";
        }
        Student s = sOpt.get();

        s.setFirstName(firstName);
        s.setLastName(lastName);
        s.setPhone(phone);
        s.setTeacherId(teacherId);
        s.setNeedsBook(Boolean.TRUE.equals(book));

        // set timeSlotId (Student has field timeSlotId)
        s.setTimeSlotId(timeSlotId);

        if (packageId != null) {
            LessonPackage pkg = lessonPackageRepository.findById(packageId).orElse(null);
            if (pkg != null) {
                s.assignPackage(pkg);
                if (pkg.getLessonsCount() != null) {
                    s.setRemainingLessons(pkg.getLessonsCount());
                }
                BigDecimal paid = initialPayment == null ? BigDecimal.ZERO : initialPayment;
                BigDecimal newDebt = pkg.getPrice() == null ? BigDecimal.ZERO : pkg.getPrice().subtract(paid);
                if (newDebt.compareTo(BigDecimal.ZERO) < 0) newDebt = BigDecimal.ZERO;
                s.setDebt(newDebt);
            }
        } else {
            // no package change — if explicit debt provided, set it
            if (debt != null) {
                s.setDebt(debt);
            }
        }

        studentService.updateStudent(s);
        return "redirect:/manager/student_list?success=updated";
    }

    @PostMapping("/delete_student")
    public String deleteStudent(@RequestParam Long studentId) {
        studentService.deleteById(studentId);
        return "redirect:/manager/student_list?success=deleted";
    }

    /* ----------------------------------------------------------------------- */
}