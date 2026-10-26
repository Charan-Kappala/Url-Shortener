package com.urlshortener.config;

import com.urlshortener.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Applies the per-client limits to link creation (POST /api/v1/urls) and redirects (GET /{code}).
 * The client is identified by IP. Behind a load balancer, server.forward-headers-strategy=native makes
 * getRemoteAddr() return the original client from X-Forwarded-For when the request comes from a trusted
 * (private-network) proxy.
 */
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;
    private final RateLimitConfig rateLimitConfig;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String ip = request.getRemoteAddr();
        String method = request.getMethod();

        if (method.equals("POST") && request.getRequestURI().equals("/api/v1/urls")) {
            return check(response, "create", ip, rateLimitConfig.getCreatePerMinute());
        }
        if (method.equals("GET")) { // only registered for single-segment paths, i.e. redirects
            return check(response, "redirect", ip, rateLimitConfig.getRedirectPerMinute());
        }
        return true;
    }

    private boolean check(HttpServletResponse response, String action, String ip, int perMinute) throws IOException {
        RateLimitService.Decision decision = rateLimitService.tryAcquire(action, ip, perMinute);
        if (decision.allowed()) {
            return true;
        }
        long retryAfterSeconds = Math.max(1, (decision.retryAfterMillis() + 999) / 1000);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\": \"Rate limit exceeded, retry after " + retryAfterSeconds + "s\"}");
        return false;
    }
}
