package com.example.attendance.config;

import com.example.attendance.service.AppUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
public class SecurityConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationSuccessHandler successHandler() {
        return (request, response, authentication) -> {
            var authorities = authentication.getAuthorities();
            if (authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                response.sendRedirect("/admin");
                return;
            }
            if (authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"))) {
                response.sendRedirect("/manager");
                return;
            }
            if (authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_TEACHER"))) {
                response.sendRedirect("/teacher");
                return;
            }
            response.sendRedirect("/");
        };
    }

    /**
     * Create DaoAuthenticationProvider and let Spring inject AppUserService and passwordEncoder as method parameters.
     * Using method parameters avoids constructor-level circular dependency between SecurityConfig and AppUserService.
     */
    @Bean
    public DaoAuthenticationProvider authProvider(AppUserService appUserService, BCryptPasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(appUserService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, DaoAuthenticationProvider authProvider) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/h2-console/**", "/login").permitAll()
                        .requestMatchers("/teacher/**").hasRole("TEACHER")
                        .requestMatchers("/manager/**").hasRole("MANAGER")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(f -> f
                        .loginPage("/login")
                        .permitAll()
                        .successHandler(successHandler())
                )
                .logout(Customizer.withDefaults())
                .authenticationProvider(authProvider);

        return http.build();
    }
}