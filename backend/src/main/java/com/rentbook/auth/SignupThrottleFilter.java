package com.rentbook.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Accounts are created in two places: landlord registration and accepting an invite. Each client address
 * may do that only so many times an hour, so neither endpoint can be used to mass-create accounts.
 * Registration is counted where it starts, at the emailed code: an account needs a code, and counting
 * there also stops the endpoint being used to flood someone's inbox. Password-reset codes are counted
 * with them for the same reason.
 * Counts live in memory, which is enough for one instance; several instances would share a store.
 */
@Component
class SignupThrottleFilter extends OncePerRequestFilter {

    static final Duration WINDOW = Duration.ofHours(1);

    private static final Pattern ACCEPT = Pattern.compile("^/api/v1/invites/[^/]+/accept$");

    private final int perWindow;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    private record Window(Instant start, int count) {
    }

    SignupThrottleFilter(@Value("${rentbook.auth.signups-per-hour:20}") int perWindow, Clock clock) {
        this.perWindow = perWindow;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !"POST".equals(request.getMethod())
                || !(path.equals("/api/v1/auth/register/code") || path.equals("/api/v1/auth/password/code") || ACCEPT.matcher(path).matches());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Instant now = clock.instant();
        if (windows.size() > 10_000) {
            windows.values().removeIf(window -> expired(window, now));
        }
        Window window = windows.compute(request.getRemoteAddr(), (client, current) ->
                current == null || expired(current, now) ? new Window(now, 1) : new Window(current.start(), current.count() + 1));
        if (window.count() > perWindow) {
            long wait = Math.max(1, Duration.between(now, window.start().plus(WINDOW)).toSeconds());
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(wait));
            response.setContentType("application/problem+json");
            response.getWriter().write("""
                    {"type":"about:blank","title":"Too Many Requests","status":429,"code":"too_many_signups",\
                    "detail":"Too many requests from this network. Try again in an hour."}""");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean expired(Window window, Instant now) {
        return !window.start().plus(WINDOW).isAfter(now);
    }
}
