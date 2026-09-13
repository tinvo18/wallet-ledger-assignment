package com.example.walletledger.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Configuration
public class ObservabilityConfig {
    private static final Logger log = LoggerFactory.getLogger(ObservabilityConfig.class);

    @Bean
    OncePerRequestFilter requestLoggingFilter(MeterRegistry meterRegistry) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {
                var traceId = UUID.randomUUID().toString().replace("-", "");
                var started = System.nanoTime();
                MDC.put("traceId", traceId);
                response.setHeader("X-Trace-Id", traceId);
                try {
                    filterChain.doFilter(request, response);
                } finally {
                    var elapsedMs = (System.nanoTime() - started) / 1_000_000.0;
                    var method = request.getMethod();
                    var path = request.getRequestURI();
                    var status = response.getStatus();
                    log.info("http_request method={} path={} status={} duration_ms={}", method, path, status, elapsedMs);
                    meterRegistry.counter("http.server.requests.total",
                            "method", method,
                            "path", path,
                            "status", String.valueOf(status)).increment();
                    Timer.builder("http.server.requests.duration")
                            .description("HTTP request duration")
                            .tags("method", method, "path", path, "status", String.valueOf(status))
                            .register(meterRegistry)
                            .record((long) (elapsedMs * 1_000_000), java.util.concurrent.TimeUnit.NANOSECONDS);
                    MDC.remove("traceId");
                }
            }
        };
    }
}
