package com.voiceover.backend.services;

import com.voiceover.backend.entities.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TranslationReviewServiceTest {
    final ContentPipelineFixture f = new ContentPipelineFixture();

    @Test void publicationRequiresCurrentDefaultLanguageFileAndCompletedTranslation() {
        when(f.languages.findByIsDefaultTrue()).thenReturn(Optional.of(f.vi));
        Poi proposed = Poi.builder().descriptionSource(f.poi.getDescriptionSource())
                .sourceLanguageCode(f.poi.getSourceLanguageCode()).build();
        assertThrows(IllegalArgumentException.class, () -> f.service.requireDefaultAudioForPublication(proposed, proposed));
        f.enqueue();
        assertThrows(IllegalArgumentException.class, () -> f.service.requireDefaultAudioForPublication(proposed, f.poi));
        f.finish("vi", "Vietnamese narration");
        assertDoesNotThrow(() -> f.service.requireDefaultAudioForPublication(proposed, f.poi));
        proposed.setDescriptionSource("New description");
        assertThrows(IllegalArgumentException.class, () -> f.service.requireDefaultAudioForPublication(proposed, f.poi));
        proposed.setDescriptionSource(f.poi.getDescriptionSource());
        f.files.get("vi").setStatus("FAILED");
        assertThrows(IllegalArgumentException.class, () -> f.service.requireDefaultAudioForPublication(proposed, f.poi));
        f.files.get("vi").setStatus("COMPLETED");
        when(f.generator.hasUsableFile(f.files.get("vi").getFilePath())).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> f.service.requireDefaultAudioForPublication(proposed, f.poi));
    }

    @Test void createsAllPendingJobsAndSnapshotsBeforeAnyProviderWork() throws Exception {
        f.enqueue();
        assertEquals(1, f.poi.getContentRevision()); assertEquals(2, f.jobs.size());
        for (var task : f.jobs) {
            assertEquals("PENDING", task.getStatus()); assertEquals(1, task.getSourceRevision());
            assertEquals("Bonjour Saigon", task.getSourceText()); assertEquals("fr", task.getSourceLanguageCode());
            assertEquals("[]", task.getProtectedTermsJson()); assertNotNull(task.getSourceHash());
        }
        assertEquals(2, f.service.dispatchableIds().size());
        verify(f.events, times(2)).publishEvent(any(ReviewAudioRequested.class)); verify(f.generator, never()).generate(any(), any(), any());
    }

    @Test void newerSourceSupersedesEveryOldJobAndRejectsLateTranslationAndFailure() {
        f.enqueue(); var old = f.claim("en"); long oldId = old.taskId();
        f.poi.setDescriptionSource("New source B"); f.enqueue();
        assertEquals(2, f.poi.getContentRevision()); assertEquals(4, f.jobs.size());
        assertNull(f.service.saveTranslation(old, "Late A"));
        f.service.fail(old, true, "Late error");
        assertEquals("PENDING", f.translations.get("en").getStatus()); assertNull(f.translations.get("en").getContent());
        assertEquals("FAILED", f.jobs.stream().filter(t -> t.getId().equals(oldId)).findFirst().orElseThrow().getStatus());
        assertEquals("PENDING", f.latest("en").getStatus()); assertEquals("New source B", f.latest("en").getSourceText());
    }

    @Test void lateAudioCannotReplaceNewSourceOrItsAudio() {
        f.enqueue(); var old = f.translate("en", "English A");
        f.poi.setDescriptionSource("B"); f.enqueue(); f.finish("en", "English B");
        String url = f.files.get("en").getFilePath();
        assertFalse(f.service.complete(old, new AudioGenerationService.GeneratedAudio("/late-A.wav", 1)));
        f.service.fail(old, false, "Old failure");
        assertEquals("English B", f.translations.get("en").getContent());
        assertEquals(url, f.files.get("en").getFilePath()); assertEquals("COMPLETED", f.latest("en").getStatus());
    }

    @Test void aToBToAStillRejectsTheOriginalAWorker() {
        f.enqueue(); var old = f.claim("en"); String originalHash = f.poi.getSourceFingerprint();
        f.poi.setDescriptionSource("B"); f.enqueue(); f.poi.setDescriptionSource("Bonjour Saigon"); f.enqueue();
        assertEquals(originalHash, f.poi.getSourceFingerprint()); assertEquals(3, f.poi.getContentRevision());
        assertNull(f.service.saveTranslation(old, "Outdated first A")); assertEquals("PENDING", f.latest("en").getStatus());
    }

    @Test void sourceLanguageAndProtectedTermsInvalidateButTermOrderAndMetadataDoNot() {
        f.enqueue(); f.finish("vi", "VI"); f.finish("en", "EN");
        f.poi.setName("Renamed"); f.enqueue(); assertEquals(2, f.jobs.size());
        f.poi.setSourceLanguageCode("en"); f.enqueue(); assertEquals(2, f.poi.getContentRevision());
        when(f.terms.findAll()).thenReturn(List.of(ProtectedTerm.builder().term("Saigon").build(), ProtectedTerm.builder().term("Long Saigon").build()));
        f.enqueue(); assertEquals(3, f.poi.getContentRevision());
        assertEquals(List.of("Long Saigon", "Saigon"), ContentFingerprint.readTerms(f.latest("en").getProtectedTermsJson()));
        when(f.terms.findAll()).thenReturn(List.of(ProtectedTerm.builder().term("Long Saigon").build(), ProtectedTerm.builder().term("Saigon").build()));
        f.enqueue(); assertEquals(3, f.poi.getContentRevision()); assertEquals(6, f.jobs.size());
    }

    @Test void missingAudioQueuesOnlyTtsWithoutTranslatingAgain() {
        f.enqueue(); f.finish("vi", "VI"); f.finish("en", "EN");
        when(f.generator.hasUsableFile(f.files.get("en").getFilePath())).thenReturn(false);
        f.enqueue(); assertEquals(3, f.jobs.size()); assertEquals("TTS_ONLY", f.latest("en").getTaskType());
        var work = f.claim("en"); assertEquals("EN", work.text()); assertEquals("COMPLETED", f.translations.get("en").getStatus());
    }

    @Test void changingSourceDuringManualAudioKeepsManualTextStaleAndAllowsExplicitConfirmation() {
        f.enqueue(); f.finish("vi", "VI"); f.finish("en", "EN");
        f.service.submit(1L, "en", "Manual English", revision("en"), false);
        var old = f.claim("en");
        f.poi.setDescriptionSource("New source"); f.enqueue();
        assertEquals("Manual English", f.translations.get("en").getContent()); assertEquals("STALE", f.translations.get("en").getStatus());
        assertNull(f.files.get("en").getFilePath()); assertFalse(f.service.complete(old, new AudioGenerationService.GeneratedAudio("/old.wav", 1)));
        assertThrows(IllegalArgumentException.class, () -> f.service.submit(1L, "en", null, revision("en"), true));
        f.service.submit(1L, "en", "Manual English", revision("en"), false);
        assertEquals("PENDING", f.latest("en").getStatus()); assertEquals(2, f.latest("en").getSourceRevision());
        assertEquals("COMPLETED", f.translations.get("en").getStatus()); assertEquals(f.poi.getSourceFingerprint(), f.translations.get("en").getSourceHash());
    }

    @Test void editQueuesOnlySelectedLanguageAndRegenerationUsesStoredText() {
        f.enqueue(); f.finish("vi", "VI"); f.finish("en", "EN");
        var result = f.service.submit(1L, "en", "Manual", revision("en"), false);
        assertEquals("VI", f.translations.get("vi").getContent()); assertEquals(3, f.jobs.size());
        assertEquals("PENDING", result.review().task().status()); assertEquals("TTS_ONLY", result.review().task().type());
        var work = f.claim("en"); assertEquals("Manual", work.text());
        f.service.complete(work, new AudioGenerationService.GeneratedAudio("/new.wav", 12));
        f.service.submit(1L, "en", null, revision("en"), true);
        assertEquals("Manual", f.claim("en").text());
    }

    @Test void staleBusyBlankOversizeAndInactiveEditsAreRejected() {
        f.enqueue(); assertThrows(ContentReviewConflictException.class, () -> f.service.submit(1L, "en", "New", revision("en"), false));
        f.finish("en", "EN");
        assertThrows(ContentReviewConflictException.class, () -> f.service.submit(1L, "en", "New", "old", false));
        assertThrows(IllegalArgumentException.class, () -> f.service.submit(1L, "en", " ", revision("en"), false));
        assertThrows(IllegalArgumentException.class, () -> f.service.submit(1L, "en", "ạ".repeat(21000), revision("en"), false));
        f.en.setActive(false); assertThrows(IllegalArgumentException.class, () -> f.service.submit(1L, "en", "New", revision("en"), false));
        assertEquals("EN", f.translations.get("en").getContent());
    }

    @Test void failedManualAudioPreservesTextAndDoesNotAutomaticallyLoopOnPoiSave() {
        f.enqueue(); f.finish("en", "EN");
        f.service.submit(1L, "en", "Manual", revision("en"), false); var work = f.claim("en");
        f.service.fail(work, false, "TTS unavailable"); f.enqueue();
        assertEquals(3, f.jobs.size()); assertEquals("FAILED", f.latest("en").getStatus());
        assertEquals("Manual", f.translations.get("en").getContent()); assertEquals("COMPLETED", f.translations.get("en").getStatus());
        f.service.submit(1L, "en", null, revision("en"), true); assertEquals("PENDING", f.latest("en").getStatus());
    }

    @Test void activeLeasePreventsDuplicateClaimAndExpiredLeaseHasNewToken() {
        f.enqueue(); var first = f.claim("en"); assertNull(f.claim("en"));
        f.latest("en").setLeaseUntil(LocalDateTime.now().minusSeconds(1));
        var second = f.claim("en"); assertNotEquals(first.claimToken(), second.claimToken());
        assertNull(f.service.saveTranslation(first, "Lost claim")); f.service.fail(first, true, "Lost failure");
        assertEquals("PROCESSING", f.latest("en").getStatus());
        assertNotNull(f.service.saveTranslation(second, "Current claim"));
    }

    @Test void restartAfterTranslationResumesAudioWithoutRepeatingTranslation() {
        f.enqueue(); var first = f.translate("en", "Persisted translation");
        f.latest("en").setLeaseUntil(LocalDateTime.now().minusSeconds(1));
        var restarted = new TranslationReviewService(f.pois, f.languages, f.texts, f.audios, f.tasks, f.events, f.generator, f.terms, f.logs);
        assertTrue(restarted.dispatchableIds().contains(first.taskId())); var recovered = restarted.claim(first.taskId());
        assertEquals("Persisted translation", recovered.text()); assertNotEquals(first.claimToken(), recovered.claimToken());
        assertFalse(restarted.complete(first, new AudioGenerationService.GeneratedAudio("/old.wav", 1)));
        assertTrue(restarted.complete(recovered, new AudioGenerationService.GeneratedAudio("/recovered.wav", 10)));
        assertEquals("/recovered.wav", f.files.get("en").getFilePath());
    }

    @Test void attemptBudgetSurvivesReclaimAndTranslationAndAudioBudgetsAreSeparate() {
        f.enqueue(); var work = f.claim("en");
        assertEquals(1, f.service.nextAttempt(work, true)); assertEquals(2, f.service.nextAttempt(work, true));
        f.latest("en").setLeaseUntil(LocalDateTime.now().minusSeconds(1)); work = f.claim("en");
        assertEquals(3, f.service.nextAttempt(work, true));
        work = f.service.saveTranslation(work, "EN"); assertEquals(1, f.service.nextAttempt(work, false));
        assertEquals(3, f.latest("en").getTranslationAttempts()); assertEquals(1, f.latest("en").getAudioAttempts());
        assertEquals(0, f.service.nextAttempt(work, true)); assertEquals("FAILED", f.latest("en").getStatus());
    }

    @Test void heartbeatRenewsOnlyTheCurrentClaim() {
        f.enqueue(); var work = f.claim("en");
        f.latest("en").setLeaseUntil(LocalDateTime.now().plusSeconds(1)); f.service.renewLease(work);
        assertTrue(f.latest("en").getLeaseUntil().isAfter(LocalDateTime.now().plusSeconds(50)));
    }

    @Test void changedTranslationCannotAcceptAnAudioResult() {
        f.enqueue(); var work = f.translate("en", "EN"); f.translations.get("en").setContent("Changed elsewhere");
        assertFalse(f.service.complete(work, new AudioGenerationService.GeneratedAudio("/stale.wav", 10)));
        assertNull(f.files.get("en").getFilePath()); assertEquals("FAILED", f.latest("en").getStatus());
    }

    @Test void unsupportedLanguageCompletesWithoutAFile() {
        f.en.setTtsSupported(false); f.enqueue(); var work = f.translate("en", "EN");
        assertTrue(f.service.complete(work, null)); assertEquals("TEXT_ONLY", f.files.get("en").getStatus());
        assertEquals("COMPLETED", f.latest("en").getStatus());
    }

    @Test void missingProviderVoiceIsTextOnlyEvenWhenLanguageFlagAllowsTts() {
        when(f.generator.supportsLanguage("en")).thenReturn(false);
        f.enqueue(); var work = f.translate("en", "EN");
        assertFalse(work.ttsSupported());
        assertFalse(f.service.detail(1L, "en").review().ttsSupported());
        assertTrue(f.service.complete(work, null));
        assertEquals("TEXT_ONLY", f.files.get("en").getStatus());
        assertThrows(IllegalArgumentException.class, () -> f.service.submit(1L, "en", null, revision("en"), true));
    }

    @Test void rawSourceChangeCannotBypassFingerprintPublicationCheck() {
        f.enqueue(); var work = f.claim("en"); f.poi.setDescriptionSource("Changed outside save");
        assertNull(f.service.saveTranslation(work, "Stale")); assertEquals("FAILED", f.latest("en").getStatus());
    }

    @Test void readDoesNotSaveOrGenerateAndRevisionIgnoresTimestampPrecision() {
        f.enqueue(); f.finish("en", "EN"); clearInvocations(f.texts, f.audios, f.tasks, f.events, f.generator);
        String before = revision("en"); f.translations.get("en").setUpdatedAt(LocalDateTime.now());
        assertEquals(before, revision("en")); assertEquals(2, f.service.index(1L).languages().size());
        verify(f.texts, never()).saveAndFlush(any()); verify(f.audios, never()).saveAndFlush(any()); verify(f.tasks, never()).saveAndFlush(any()); verifyNoInteractions(f.events);
    }

    String revision(String code) { return TranslationReviewService.revision(f.translations.get(code)); }
}
