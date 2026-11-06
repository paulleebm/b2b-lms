package com.lms.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a trace id — taken from a W3C {@code traceparent} header if the BFF sent
 * one, otherwise generated — and puts it in the log MDC, the response header and error bodies.
 * Day 5 replaces this with OpenTelemetry, which uses the same W3C format.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "traceId";
    private static final Pattern TRACEPARENT = Pattern.compile("^00-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = fromTraceparent(request.getHeader("traceparent"));
        MDC.put(MDC_KEY, traceId);
        response.setHeader("X-Trace-Id", traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String fromTraceparent(String header) {
        if (header != null) {
            var m = TRACEPARENT.matcher(header);
            if (m.matches()) {
                return m.group(1);
            }
        }
        byte[] bytes = new byte[16];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
