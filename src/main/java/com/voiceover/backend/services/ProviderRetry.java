package com.voiceover.backend.services;

import java.io.IOException;

final class ProviderRetry {
    private ProviderRetry() {}

    static boolean isRetryable(Exception error) {
        if (error instanceof InterruptedException || error instanceof IllegalArgumentException
                || error instanceof IllegalStateException) return false;
        if (error instanceof ProviderRequestException providerError) return providerError.isRetryable();
        return error instanceof IOException;
    }

    static void pause(int failedAttempt) throws InterruptedException {
        Thread.sleep(Math.min(4000L, 500L << Math.max(0, failedAttempt - 1)));
    }
}
