package com.ringgrank.filter;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RequestResponseLoggingFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(RequestResponseLoggingFilter.class);

    @Value("${logging.enabled:true}")
    private boolean isEnabled;

    @Override
    protected void doFilterInternal(@SuppressWarnings("null") HttpServletRequest request,
            @SuppressWarnings("null") HttpServletResponse response, @SuppressWarnings("null") FilterChain filterChain)
            throws ServletException, IOException {
        if (!isEnabled) {
            filterChain.doFilter(request, response);
            return;
        }
        long startTime = System.currentTimeMillis();
        StringBuilder logMessage = new StringBuilder();

        // Log request basics
        logMessage.append(String.format("Method=%s, ", request.getMethod()));
        logMessage.append(String.format("URI=%s, ", request.getRequestURI()));
        logMessage.append(String.format("Query=%s, ",
                request.getQueryString() != null ? request.getQueryString() : "None"));
        logMessage.append(String.format("ClientIP=%s", request.getRemoteAddr()));
        try {
            filterChain.doFilter(request, response);
        } finally {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            logMessage.append(String.format(", ResponseCode=%s, Duration=%dms", response.getStatus(), duration));
            logger.info(logMessage.toString());
        }
    }
}
