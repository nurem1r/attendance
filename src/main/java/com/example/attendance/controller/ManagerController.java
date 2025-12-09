package com.example.attendance.controller;

import com.example.attendance.entities.*;
import com.example.attendance.service.AppUserService;
import com.example.attendance.service.AttendanceService;
import com.example.attendance.service.PaymentService;
import com.example.attendance.service.StudentService;
import com.example.attendance.service.TeacherService;
import com.example.attendance.service.TimeSlotService;
import com.example.attendance.repository.LessonPackageRepository;
import com.example.attendance.repository.StudentRepository;
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
    private final StudentRepository studentRepository;

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
            Teacher teacher = teacherService.createTeacherForUser(user, firstName, lastName, phone, shift);
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
        model.addAttribute("packageTypes", com.example.attendance.enums.PackageType.values());
        model.addAttribute("teachers", teacherService.findAll());
        return "manager/add_student";
    }

    @PostMapping("/manager/add_student")
    public String createStudent(@RequestParam String firstName,
                                @RequestParam String lastName,
                                @RequestParam(required=false) String phone,
                                @RequestParam Long packageId,
                                @RequestParam(required=false, defaultValue = "0") BigDecimal initialPayment,
                                Model model) {
        LessonPackage pkg = lessonPackageRepository.findById(packageId).orElse(null);
        if (pkg == null) {
            model.addAttribute("error", "Package not found");
            return "manager/add_student";
        }

        Student s = new Student();
        s.setFirstName(firstName);
        s.setLastName(lastName);
        s.setPhone(phone);
        // привязка пакета
        s.setPackageCode(pkg.getCode()); // или s.setPackageId(pkg.getId());
        s.setPackagePrice(pkg.getPrice());
        // debt = package.price - initialPayment (если >0)
        BigDecimal paid = initialPayment == null ? BigDecimal.ZERO : initialPayment;
        BigDecimal debt = pkg.getPrice().subtract(paid);
        if (debt.compareTo(BigDecimal.ZERO) < 0) debt = BigDecimal.ZERO; // не позволяем отрицательный долг
        s.setDebt(debt);
        // сохранить студента
        studentRepository.save(s);

        // при желании — создать платежную запись Payment с sum=paid, связать со студентом

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
}