package com.hackathon.distress.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// Runs before every request.
// 1. The API is only for this computer. If the request comes through some other
//    host name (a tunnel, or a DNS rebinding attack), only the "acknowledge"
//    link is allowed, everything else gets 403.
// 2. Adds basic security headers to every response.
@Component
public class LocalOnlyFilter extends OncePerRequestFilter {

    // localhost + 127.0.0.1 normally. In Docker also "core-api" (the analysis service calls us by that name).
    @Value("${app.allowed-hosts}")
    private List<String> allowedHosts;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Cache-Control", "no-store");

        String host = request.getServerName();
        boolean local = allowedHosts.contains(host) || host.equals("[::1]") || host.equals("::1");
        boolean ackLink = request.getRequestURI().startsWith("/api/alerts/ack/");

        if (!local && !ackLink) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Only available on this computer");
            return;
        }
        chain.doFilter(request, response);
    }
}
