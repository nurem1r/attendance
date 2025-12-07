package com.example.attendance.controller;

import com.example.attendance.entities.AppUser;
import com.example.attendance.entities.Payment;
import com.example.attendance.service.AppUserService;
import com.example.attendance.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final AppUserService appUserService;

    /**
     * Handle payment form submission.
     * Only authenticated users can access; we additionally allow only MANAGER or ADMIN (checked via authorities).
     */
    @PostMapping("/payments/pay")
    public String pay(@RequestParam Long studentId,
                      @RequestParam BigDecimal amount,
                      @RequestParam(required = false) String note,
                      Authentication authentication,
                      HttpServletRequest request,
                      RedirectAttributes redirectAttributes) {

        // check role
        boolean allowed = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER") || a.getAuthority().equals("ROLE_ADMIN"));
        if (!allowed) {
            redirectAttributes.addFlashAttribute("error", "No permission to add payments");
            String referer = request.getHeader("Referer");
            return "redirect:" + (referer != null ? referer : "/");
        }

        String username = authentication.getName();
        AppUser user = (AppUser) appUserService.loadUserByUsername(username);
        Long userId = user.getId();

        try {
            Payment p = paymentService.makePayment(studentId, amount, userId, note);
            redirectAttributes.addFlashAttribute("success", "Payment recorded: " + p.getAmount());
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Payment failed: " + ex.getMessage());
        }

        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/manager/student_list");
    }
}