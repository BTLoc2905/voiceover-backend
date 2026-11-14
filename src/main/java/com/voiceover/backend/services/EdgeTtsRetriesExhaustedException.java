package com.voiceover.backend.services;

import java.io.IOException;

/** The worker already used its three attempts; the pipeline must not multiply them. */
final class EdgeTtsRetriesExhaustedException extends IOException {
    EdgeTtsRetriesExhaustedException(String message) {
        super(message);
    }
}
