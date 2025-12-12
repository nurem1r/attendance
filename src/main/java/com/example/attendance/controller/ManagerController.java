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

        // Загрузить всех студентов один раз
        List<Student> allStudents = studentService.findAll();

        // Общая сумма студентов
        long totalStudents = allStudents == null ? 0L : allStudents.size();

        // Map teacherUserId -> count
        Map<Long, Long> studentsCountMap = new HashMap<>();
        if (allStudents != null) {
            for (Student s : allStudents) {
                Long tid = s.getTeacherId();
                if (tid == null) {
                }
                studentsCountMap.merge(tid, 1L, Long::sum);
            }
        }

        model.addAttribute("teachers", teachers);
        model.addAttribute("studentsCountMap", studentsCountMap);
        model.addAttribute("totalStudents", totalStudents);
        return "manager/dashboard";
    }

    /**
     * Create teacher: check username first, create AppUser, then Teacher.
     * If Teacher creation fails after AppUser created — rollback AppUser to avoid orphans.
     */
    @GetMapping("/add_teacher")
    public String addTeacherForm(Model model) {
        // Если нужно, можно передать какие-то данные в форму (например default shift)
        model.addAttribute("shifts", com.example.attendance.enums.Shift.values());
        return "manager/add_teacher";
    }

    @PostMapping("/add_teacher")
    public String addTeacher(@RequestParam String firstName,
                             @RequestParam String lastName,
                             @RequestParam String phone,
                             @RequestParam com.example.attendance.enums.Shift shift,
                             @RequestParam String username,
                             @RequestParam String password) {
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
        List<LessonPackage> packages = lessonPackageRepository.findAll();
        model.addAttribute("packages", packages);

        model.addAttribute("teachers", teacherService.findAll());
        return "manager/add_student";
    }

    @PostMapping("/add_student")
    public String addStudent(@RequestParam String firstName,
                             @RequestParam String lastName,
                             @RequestParam(required = false) String phone,
                             @RequestParam(required = false) Long packageId,
                             @RequestParam(required = false) PackageType packageType,
                             @RequestParam Long teacherId,
                             @RequestParam(required = false) Long timeSlotId,
                             @RequestParam(required = false) Boolean book,
                             @RequestParam(required = false) BigDecimal initialPayment,
                             @RequestParam(required = false) BigDecimal debt,
                             @RequestParam(required = false) String paymentNote) {

        if (packageId != null) {
            studentService.createStudentWithPackage(firstName, lastName, phone, packageId, teacherId, timeSlotId, book, initialPayment, paymentNote);
        } else {
            studentService.createStudent(firstName, lastName, phone, packageType, teacherId, timeSlotId, book, debt, paymentNote);
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

        List<Teacher> teachers = teacherService.findAll();
        Map<Long, Teacher> teacherMap = teachers.stream().collect(Collectors.toMap(Teacher::getUserId, t -> t));

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
        model.addAttribute("q", q);

        return "manager/student_list";
    }

    @GetMapping("/teacher_list")
    public String teacherList(Model model) {
        List<Teacher> teachers = teacherService.findAll();

        Map<Long, Long> studentsCountMap = new HashMap<>();
        for (Teacher t : teachers) {
            Long tid = t.getUserId();
            if (tid != null) {
                try {
                    long cnt = studentService.countByTeacherId(tid);
                    studentsCountMap.put(tid, cnt);
                } catch (Exception ex) {
                    log.warn("Failed to count students for teacherId={}", tid, ex);
                    studentsCountMap.put(tid, 0L);
                }
            }
        }

        model.addAttribute("teachers", teachers);
        model.addAttribute("studentsCountMap", studentsCountMap);
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

        List<LessonPackage> packages = lessonPackageRepository.findAll();
        model.addAttribute("packages", packages);
        model.addAttribute("teachers", teacherService.findAll());

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
                                @RequestParam(required = false) String paymentNote) {
        log.info("updateStudent called: id={}, first='{}', last='{}', phone='{}', packageId={}, teacherId={}, timeSlotId={}, book={}, initialPayment={}, paymentNote={}",
                id, firstName, lastName, phone, packageId, teacherId, timeSlotId, book, initialPayment, (paymentNote == null ? null : paymentNote.trim()));

        Optional<Student> sOpt = studentService.findById(id);
        if (sOpt.isEmpty()) {
            log.warn("updateStudent: student not found id={}", id);
            return "redirect:/manager/student_list?error=not_found";
        }
        Student s = sOpt.get();

        s.setFirstName(firstName);
        s.setLastName(lastName);
        s.setPhone(phone);
        s.setTeacherId(teacherId);
        s.setNeedsBook(Boolean.TRUE.equals(book));

        s.setTimeSlotId(timeSlotId);

        BigDecimal previousDebt = s.getDebt() == null ? BigDecimal.ZERO : s.getDebt();

        if (packageId != null) {
            LessonPackage pkg = lessonPackageRepository.findById(packageId).orElse(null);
            if (pkg != null) {
                Long currentPkgId = s.getLessonPackage() != null ? s.getLessonPackage().getId() : null;
                boolean changed = (currentPkgId == null && packageId != null) || (currentPkgId != null && !currentPkgId.equals(packageId));

                if (changed) {
                    // если выбран новый пакет — старая логика: назначаем пакет, считаем долг по пакету
                    s.assignPackage(pkg);
                    if (pkg.getLessonsCount() != null) {
                        s.setRemainingLessons(pkg.getLessonsCount());
                    }

                    BigDecimal paid = initialPayment == null ? BigDecimal.ZERO : initialPayment;
                    BigDecimal packagePrice = pkg.getPrice() == null ? BigDecimal.ZERO : pkg.getPrice();
                    BigDecimal newPackageDebt = packagePrice.subtract(paid);
                    if (newPackageDebt.compareTo(BigDecimal.ZERO) < 0) newPackageDebt = BigDecimal.ZERO;

                    BigDecimal resultingDebt = previousDebt.add(newPackageDebt);
                    s.setDebt(resultingDebt);

                    // save payment note if provided
                    if (paymentNote != null && !paymentNote.isBlank()) {
                        s.setPaymentNote(paymentNote.trim());
                    }

                    log.info("updateStudent: package changed for id={} -> newDebt={}", id, s.getDebt());
                } else {
                    // если пакет тот же — раньше платёж игнорировался. Сейчас: если есть initialPayment > 0, применяем его.
                    if (initialPayment != null && initialPayment.compareTo(BigDecimal.ZERO) > 0) {
                        try {
                            BigDecimal newDebt = studentService.applyPayment(s.getId(), initialPayment);
                            // обновим сущность s с новым долгом (applyPayment уже сохранил)
                            s.setDebt(newDebt);
                            log.info("updateStudent: applied payment {} for student {} -> newDebt={}", initialPayment, s.getId(), newDebt);
                        } catch (Exception ex) {
                            log.error("updateStudent: failed to apply payment for studentId={} amount={}", s.getId(), initialPayment, ex);
                            return "redirect:/manager/student_list?error=payment_failed";
                        }
                        if (paymentNote != null && !paymentNote.isBlank()) {
                            s.setPaymentNote(paymentNote.trim());
                        }
                    } else {
                        log.info("updateStudent: same package and no payment for id={}", id);
                    }
                }
            } else {
                log.warn("updateStudent: package not found id={}", packageId);
            }
        } else {
            // no package provided: возможно пользователь редактирует только другие поля.
            // Если пришёл initialPayment — применим его к долгу.
            if (initialPayment != null && initialPayment.compareTo(BigDecimal.ZERO) > 0) {
                try {
                    BigDecimal newDebt = studentService.applyPayment(s.getId(), initialPayment);
                    s.setDebt(newDebt);
                    log.info("updateStudent: applied payment {} for student {} (no package change) -> newDebt={}", initialPayment, s.getId(), newDebt);
                } catch (Exception ex) {
                    log.error("updateStudent: failed to apply payment for studentId={} amount={}", s.getId(), initialPayment, ex);
                    return "redirect:/manager/student_list?error=payment_failed";
                }
                if (paymentNote != null && !paymentNote.isBlank()) {
                    s.setPaymentNote(paymentNote.trim());
                }
            } else {
                log.info("updateStudent: no packageId and no payment for id={}", id);
            }
        }

        // Финальное сохранение (если applyPayment уже сохранён, это сделает дополнительный save — безопасно)
        studentService.updateStudent(s);
        return "redirect:/manager/student_list?success=updated";
    }

    // helper
    private Long parseLongOrNull(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
    @PostMapping("/delete_student")
    public String deleteStudent(@RequestParam Long studentId) {
        studentService.deleteById(studentId);
        return "redirect:/manager/student_list?success=deleted";
    }

    /* ----------------------------------------------------------------------- */

    // Добавьте/замените следующие методы в классе ManagerController

    @GetMapping("/edit_teacher/{id}")
    public String editTeacherForm(@PathVariable Long id, Model model) {
        if (id == null) {
            return "redirect:/manager/teacher_list?error=bad_request";
        }
        Teacher t = teacherService.findById(id);
        if (t == null) {
            return "redirect:/manager/teacher_list?error=not_found";
        }
        model.addAttribute("teacher", t);
        return "manager/edit_teacher";
    }

    @PostMapping("/edit_teacher")
    public String updateTeacher(@RequestParam Long id,
                                @RequestParam String firstName,
                                @RequestParam String lastName,
                                @RequestParam(required = false) String phone,
                                @RequestParam(required = false) com.example.attendance.enums.Shift shift) {
        if (id == null) {
            return "redirect:/manager/teacher_list?error=bad_request";
        }
        Teacher t = teacherService.findById(id);
        if (t == null) {
            return "redirect:/manager/teacher_list?error=not_found";
        }

        try {
            t.setFirstName(firstName != null ? firstName.trim() : null);
            t.setLastName(lastName != null ? lastName.trim() : null);
            t.setPhone(phone);
            t.setShift(shift);
            teacherService.updateTeacher(t);
        } catch (Exception ex) {
            log.error("Failed to update teacher id={}", id, ex);
            return "redirect:/manager/teacher_list?error=update_failed";
        }

        return "redirect:/manager/teacher_list?success=updated";
    }

    @PostMapping("/delete_teacher")
    public String deleteTeacher(@RequestParam Long teacherUserId) {
        if (teacherUserId == null) {
            return "redirect:/manager/teacher_list?error=bad_request";
        }
        try {
            teacherService.deleteById(teacherUserId);
            appUserService.deleteById(teacherUserId);
            log.info("Deleted teacher and user with id={}", teacherUserId);
        } catch (Exception ex) {
            log.error("Failed to delete teacher userId={}", teacherUserId, ex);
            return "redirect:/manager/teacher_list?error=delete_failed";
        }
        return "redirect:/manager/teacher_list?success=deleted";
    }
}