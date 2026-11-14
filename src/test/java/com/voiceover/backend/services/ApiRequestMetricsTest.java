package com.voiceover.backend.services;

import com.voiceover.backend.repositories.SystemLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiRequestMetricsTest {
    @Test void countsServerErrorsAndExceptionsButNotClientErrorsAsSystemErrors() throws Exception {
        var logs = mock(SystemLogRepository.class); var metrics = new ApiRequestMetrics(logs);
        for (int status : new int[]{200, 404, 503}) {
            var response = new MockHttpServletResponse();
            metrics.doFilter(new MockHttpServletRequest("GET", "/api/public/pois"), response, (request, result) -> response.setStatus(status));
        }
        assertThrows(IOException.class, () -> metrics.doFilter(new MockHttpServletRequest("GET", "/api/public/pois"),
                new MockHttpServletResponse(), (request, result) -> { throw new IOException("offline"); }));
        var snapshot = metrics.snapshot();
        assertEquals(4, snapshot.requests()); assertEquals(2, snapshot.errors()); assertEquals(50.0, snapshot.errorRatePercent());
        assertTrue(snapshot.averageLatencyMs() >= 0); verify(logs, times(2)).save(any());
    }

    @Test void nonApiTrafficDoesNotPolluteMetricsAndAnEmptyWindowHasNoFakeLatency() throws Exception {
        var logs = mock(SystemLogRepository.class); var metrics = new ApiRequestMetrics(logs);
        metrics.doFilter(new MockHttpServletRequest("GET", "/images/test.png"), new MockHttpServletResponse(), (request, result) -> {});
        assertEquals(0, metrics.snapshot().requests()); assertNull(metrics.snapshot().averageLatencyMs());
        assertNull(metrics.snapshot().errorRatePercent()); verifyNoInteractions(logs);
    }
}
