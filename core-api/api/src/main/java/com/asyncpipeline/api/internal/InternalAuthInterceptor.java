package com.asyncpipeline.api.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class InternalAuthInterceptor implements HandlerInterceptor {

    private final String sharedSecret;

    public InternalAuthInterceptor(
            @Value("${app.internal.shared-secret:}") String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (sharedSecret.isEmpty()) {
            return true; // no auth required in dev mode
        }

        String provided = request.getHeader("X-Internal-Secret");
        if (sharedSecret.equals(provided)) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Forbidden\"}");
        return false;
    }
}
