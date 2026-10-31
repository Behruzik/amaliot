package com.example.amaliyot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Bounded, per-process protection for expensive authentication calls. */
public final class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final Set<String> PATHS = Set.of("/api/auth/login", "/api/auth/register",
            "/api/auth/password/forgot", "/api/auth/password/reset", "/api/auth/password/change");
    private final Map<String, Window> windows = new HashMap<>();
    private final int maxRequests;
    private final int maxClients;
    private final Duration windowDuration;
    private final Clock clock;

    public AuthRateLimitFilter(int maxRequests, int windowSeconds, int maxClients, Clock clock) {
        if (maxRequests < 1 || windowSeconds < 1 || maxClients < 1)
            throw new IllegalArgumentException("Authentication rate-limit settings must be positive");
        this.maxRequests = maxRequests;
        this.maxClients = maxClients;
        this.windowDuration = Duration.ofSeconds(windowSeconds);
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equals(request.getMethod()) || !PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Never trust caller-supplied X-Forwarded-For. Behind a trusted proxy,
        // apply a shared edge limiter or explicitly configure trusted forwarding.
        long retryAfter = acquire(request.getRemoteAddr());
        if (retryAfter > 0) {
            response.setHeader("Retry-After", Long.toString(retryAfter));
            SecurityErrorWriter.write(request, response, 429, "AUTH_RATE_LIMITED",
                    "Too many authentication requests; try again after the Retry-After interval");
            return;
        }
        chain.doFilter(request, response);
    }

    private synchronized long acquire(String address) {
        Instant now = clock.instant();
        windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt));
        Window window = windows.get(address);
        if (window == null) {
            // A full table must not evict still-active limits.
            if (windows.size() >= maxClients) return Math.max(1, windowDuration.getSeconds());
            window = new Window(now.plus(windowDuration));
            windows.put(address, window);
        }
        if (window.requests >= maxRequests) {
            long remainingMillis = Duration.between(now, window.expiresAt).toMillis();
            return Math.max(1, (remainingMillis + 999) / 1000);
        }
        window.requests++;
        return 0;
    }

    private static final class Window {
        private final Instant expiresAt;
        private int requests;
        private Window(Instant expiresAt) { this.expiresAt = expiresAt; }
    }
}
