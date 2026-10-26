package com.voiceover.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import com.voiceover.backend.repositories.AdminAccountRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdminSessionInterceptor implements HandlerInterceptor {
    private final AdminAccountRepository accounts;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || "/api/admin/auth/login".equals(request.getRequestURI())) return true;
        var session = request.getSession(false);
        if (session != null && "ADMIN".equals(session.getAttribute("adminRole"))
                && session.getAttribute("adminId") instanceof Long id
                && accounts.findById(id).filter(value -> "ACTIVE".equalsIgnoreCase(value.getStatus())
                        && "ADMIN".equalsIgnoreCase(value.getRole())).isPresent()) return true;
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Admin login required");
        return false;
    }
}
