package com.voiceover.backend.services;

import java.io.IOException;

final class ProviderRequestException extends IOException {
    private final int statusCode;

    ProviderRequestException(String provider, int statusCode) {
        super(provider + " trả về HTTP " + statusCode + ".");
        this.statusCode = statusCode;
    }

    boolean isRetryable() {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }
}
