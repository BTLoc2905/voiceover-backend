package com.voiceover.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import com.voiceover.backend.repositories.AdminAccountRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
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
                && session.getAttribute("adminId") instanceof Long id) {
            try {
                var account = accounts.findById(id).filter(value -> "ACTIVE".equalsIgnoreCase(value.getStatus())
                        && "ADMIN".equalsIgnoreCase(value.getRole()));
                if (account.isPresent()) {
                    var credential = session.getAttribute("adminCredential");
                    if (credential != null && !credential.equals(account.get().getPasswordHash())) {
                        session.invalidate();
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Admin login required");
                        return false;
                    }
                    boolean authEndpoint = java.util.Set.of("/api/admin/auth/me", "/api/admin/auth/logout",
                            "/api/admin/auth/password").contains(request.getRequestURI());
                    if (!authEndpoint && com.voiceover.backend.services.AdminPasswordPolicy.expired(account.get(), java.time.Instant.now())) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"code\":\"PASSWORD_CHANGE_REQUIRED\",\"message\":\"Mật khẩu đã quá 24 giờ. Vui lòng đổi mật khẩu để tiếp tục.\"}");
                        return false;
                    }
                    return true;
                }
            } catch (DataAccessException | CannotCreateTransactionException error) {
                if ("/api/admin/monitoring/dashboard".equals(request.getRequestURI())) {
                    response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":\"DATABASE_UNAVAILABLE\",\"message\":\"Cơ sở dữ liệu tạm thời không khả dụng.\"}");
                    return false;
                }
                throw error;
            }
        }
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Admin login required");
        return false;
    }
}
