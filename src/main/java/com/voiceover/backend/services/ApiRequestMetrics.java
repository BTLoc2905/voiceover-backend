package com.voiceover.backend.services;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.voiceover.backend.entities.SystemLog;
import com.voiceover.backend.repositories.SystemLogRepository;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/** Rolling in-process API measurements. A restart begins a new measurement window. */
@Component
@RequiredArgsConstructor
public class ApiRequestMetrics extends OncePerRequestFilter {
    private final SystemLogRepository logs;
    private record Sample(Instant at, long millis, boolean serverError) {}
    private final Deque<Sample> samples = new ArrayDeque<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException error) {
            failed = true;
            throw error;
        } finally {
            boolean serverError = failed || response.getStatus() >= 500;
            record(Math.max(0, (System.nanoTime() - started) / 1_000_000), serverError);
            if (serverError) {
                try {
                    logs.save(SystemLog.builder().component("api").severity("ERROR")
                            .message(request.getMethod() + " " + request.getRequestURI() + " trả về HTTP "
                                    + (failed ? 500 : response.getStatus())).build());
                } catch (RuntimeException ignored) { /* Recording a failure must not mask the original response. */ }
            }
        }
    }

    private synchronized void record(long millis, boolean serverError) {
        samples.addLast(new Sample(Instant.now(), millis, serverError));
        trim();
    }

    private void trim() {
        Instant cutoff = Instant.now().minusSeconds(300);
        while (!samples.isEmpty() && (samples.peekFirst().at().isBefore(cutoff) || samples.size() > 10_000)) samples.removeFirst();
    }

    public synchronized Snapshot snapshot() {
        trim();
        long errors = samples.stream().filter(Sample::serverError).count();
        double average = samples.stream().mapToLong(Sample::millis).average().orElse(0);
        return new Snapshot(samples.size(), errors, samples.isEmpty() ? null : Math.round(average * 10.0) / 10.0,
                samples.isEmpty() ? null : Math.round(errors * 1000.0 / samples.size()) / 10.0);
    }

    public record Snapshot(long requests, long errors, Double averageLatencyMs, Double errorRatePercent) {}
}
