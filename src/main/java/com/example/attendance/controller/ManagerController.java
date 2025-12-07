package com.example.attendance.controller;

import com.example.attendance.entities.AppUser;
import com.example.attendance.entities.Student;
import com.example.attendance.entities.Teacher;
import com.example.attendance.entities.TimeSlot;
import com.example.attendance.service.AppUserService;
import com.example.attendance.service.AttendanceService;
import com.example.attendance.service.PaymentService;
import com.example.attendance.service.StudentService;
import com.example.attendance.service.TeacherService;
import com.example.attendance.service.TimeSlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/manager")
@RequiredArgsConstructor
public class ManagerController {

    private final TeacherService teacherService;
    private final AppUserService appUserService;
    private final StudentService studentService;
    private final TimeSlotService timeSlotService;
    private final PaymentService paymentService;
    private final AttendanceService attendanceService;

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

    @PostMapping("/add_teacher")
    public String addTeacher(@RequestParam String firstName,
                             @RequestParam String lastName,
                             @RequestParam String phone,
                             @RequestParam com.example.attendance.enums.Shift shift,
                             @RequestParam String username,
                             @RequestParam String password) {
        AppUser user = appUserService.createTeacherUser(username, password);
        teacherService.createTeacherForUser(user, firstName, lastName, phone, shift);
        return "redirect:/manager";
    }

    @GetMapping("/add_student")
    public String addStudentForm(Model model) {
        model.addAttribute("packageTypes", com.example.attendance.enums.PackageType.values());
        model.addAttribute("teachers", teacherService.findAll());
        return "manager/add_student";
    }

    @PostMapping("/add_student")
    public String addStudent(@RequestParam String firstName,
                             @RequestParam String lastName,
                             @RequestParam String phone,
                             @RequestParam com.example.attendance.enums.PackageType packageType,
                             @RequestParam Long teacherId,
                             @RequestParam(required = false) Long timeSlotId,
                             @RequestParam(required = false) Boolean book,
                             @RequestParam(required = false) BigDecimal debt) {
        studentService.createStudent(firstName, lastName, phone, packageType, teacherId, timeSlotId, book, debt);
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
}