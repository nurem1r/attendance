package com.example.attendance.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import java.io.IOException;

/**
 * Custom logout success handler that:
 * - invalidates session (safe guard),
 * - deletes JSESSIONID and XSRF-TOKEN cookies,
 * - sets no-cache headers so browser reloads a fresh login page,
 * - redirects to /login without query parameters.
 */
public class CustomLogoutSuccessHandler implements LogoutSuccessHandler {

    private final String redirectUrl;

    public CustomLogoutSuccessHandler(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) throws IOException, ServletException {
        // ensure session invalidated (Spring will normally do this, but be safe)
        try {
            var session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        } catch (IllegalStateException ignored) {
            // session already invalidated
        }

        // helper to delete cookie
        final String contextPath = (request.getContextPath() == null || request.getContextPath().isEmpty()) ? "/" : request.getContextPath();

        // delete JSESSIONID
        Cookie js = new Cookie("JSESSIONID", "");
        js.setPath(contextPath);
        js.setMaxAge(0);
        js.setHttpOnly(true);
        js.setSecure(request.isSecure());
        response.addCookie(js);

        // delete CSRF cookie used by CookieCsrfTokenRepository (default name "XSRF-TOKEN")
        Cookie xsrf = new Cookie("XSRF-TOKEN", "");
        xsrf.setPath(contextPath);
        xsrf.setMaxAge(0);
        // XSRF cookie usually not HttpOnly so JS can read it if configured; deleting is safe either way
        xsrf.setHttpOnly(false);
        xsrf.setSecure(request.isSecure());
        response.addCookie(xsrf);

        // set no-cache headers to force fresh load of /login
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        // redirect to bare login URL (no ?logout)
        response.sendRedirect(request.getContextPath() + redirectUrl);
    }
}