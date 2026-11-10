package com.voiceover.backend.services;

import com.voiceover.backend.entities.Language;
import com.voiceover.backend.entities.Poi;
import com.voiceover.backend.repositories.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PoiManagementValidationTest {
    private final PoiRepository pois = mock(PoiRepository.class);
    private final LanguageRepository languages = mock(LanguageRepository.class);
    private final TranslationReviewService pipeline = mock(TranslationReviewService.class);
    private final PoiService service = new PoiService(pois, mock(TranslationRepository.class), mock(AudioRepository.class),
            languages, mock(RouteStopRepository.class), pipeline, mock(PoiThumbnailService.class));

    private Poi validPoi() {
        return Poi.builder().name("  Tháp thử nghiệm  ").descriptionSource("  Nội dung mô tả  ")
                .latitude(BigDecimal.valueOf(10.2)).longitude(BigDecimal.valueOf(106.3))
                .radiusM(45).sourceLanguageCode("vi").status("draft").build();
    }

    @Test
    void rejectsInvalidRequiredFieldsCoordinatesRadiusStatusAndThumbnailUrl() {
        when(languages.findById("vi")).thenReturn(Optional.of(Language.builder().code("vi").isActive(true).build()));
        List<Consumer<Poi>> invalidCases = List.of(
                poi -> poi.setName("  "),
                poi -> poi.setDescriptionSource(" "),
                poi -> poi.setLatitude(BigDecimal.valueOf(91)),
                poi -> poi.setLongitude(BigDecimal.valueOf(-181)),
                poi -> poi.setRadiusM(0),
                poi -> poi.setStatus("ONLINE"),
                poi -> poi.setThumbnailUrl("javascript:alert(1)"),
                poi -> poi.setSourceLanguageCode("xx")
        );
        for (Consumer<Poi> invalid : invalidCases) {
            Poi poi = validPoi();
            invalid.accept(poi);
            assertThrows(IllegalArgumentException.class, () -> service.saveOrUpdatePoi(poi));
        }
        verify(pois, never()).saveAndFlush(any());
        verifyNoInteractions(pipeline);
    }

    @Test
    void trimsTextNormalizesStatusAndStartsPipelineAfterSavingValidPoi() {
        when(languages.findById("vi")).thenReturn(Optional.of(Language.builder().code("vi").isActive(true).build()));
        when(pois.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Poi saved = service.saveOrUpdatePoi(validPoi());

        assertEquals("Tháp thử nghiệm", saved.getName());
        assertEquals("Nội dung mô tả", saved.getDescriptionSource());
        assertEquals("DRAFT", saved.getStatus());
        var order = inOrder(pois, pipeline);
        order.verify(pois).saveAndFlush(saved);
        order.verify(pipeline).enqueueSource(saved);
    }

    @Test
    void preservesSeventeenDecimalPlacesAndRejectsExcessInsteadOfRounding() {
        when(languages.findById("vi")).thenReturn(Optional.of(Language.builder().code("vi").isActive(true).build()));
        when(pois.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Poi exact = validPoi();
        exact.setLatitude(new BigDecimal("10.76261234567890123"));
        exact.setLongitude(new BigDecimal("106.66021234567890123"));
        Poi saved = service.saveOrUpdatePoi(exact);
        assertEquals("10.76261234567890123", saved.getLatitude().toPlainString());
        assertEquals("106.66021234567890123", saved.getLongitude().toPlainString());

        Poi tooPrecise = validPoi();
        tooPrecise.setLatitude(new BigDecimal("10.762612345678901234"));
        assertThrows(IllegalArgumentException.class, () -> service.saveOrUpdatePoi(tooPrecise));
        verify(pois, times(1)).saveAndFlush(any());
    }

    @Test void rejectsAnOldSourceRevisionBeforeWritingAnything() {
        Poi current = validPoi(); current.setId(1L); current.setContentRevision(2);
        when(pois.findForUpdate(1L)).thenReturn(Optional.of(current));
        Poi stale = validPoi(); stale.setId(1L); stale.setContentRevision(1);
        assertThrows(ContentReviewConflictException.class, () -> service.saveOrUpdatePoi(stale));
        verify(pois, never()).saveAndFlush(any()); verifyNoInteractions(pipeline);
        assertEquals(2, current.getContentRevision());
    }

    @Test void nearbyReadFiltersRadiusAndNeverSavesOrEnqueues() {
        Poi near = validPoi(); near.setId(1L); near.setLatitude(BigDecimal.ZERO); near.setLongitude(BigDecimal.valueOf(0.00449)); near.setStatus("PUBLISHED");
        Poi far = validPoi(); far.setId(2L); far.setLatitude(BigDecimal.ZERO); far.setLongitude(BigDecimal.valueOf(0.00451)); far.setStatus("PUBLISHED");
        when(pois.findByStatus("PUBLISHED")).thenReturn(List.of(far, near));
        var result = service.getNearbyPublishedPois(0, 0, 500);
        assertEquals(1, result.size()); assertSame(near, result.getFirst().get("poi"));
        verify(pois, never()).save(any()); verify(pois, never()).saveAndFlush(any()); verifyNoInteractions(pipeline);
        assertThrows(IllegalArgumentException.class, () -> service.getNearbyPublishedPois(Double.NaN, 0, 500));
    }

    @Test void preloadFlagStopsBeyondThreeHundredMeters() {
        Poi poi = validPoi(); poi.setId(9L); poi.setStatus("PUBLISHED");
        poi.setLatitude(BigDecimal.ZERO); poi.setLongitude(BigDecimal.ZERO);
        when(pois.findByStatus("PUBLISHED")).thenReturn(List.of(poi));
        double metersPerDegree = 111194.92664455874;
        var inside = service.getNearbyPublishedPois(299 / metersPerDegree, 0, 500);
        var outside = service.getNearbyPublishedPois(301 / metersPerDegree, 0, 500);
        assertEquals(true, inside.getFirst().get("preloadAudio"));
        assertEquals(false, outside.getFirst().get("preloadAudio"));
    }

    @Test void publicationCheckRunsBeforePersistingNewPoi() {
        when(languages.findById("vi")).thenReturn(Optional.of(Language.builder().code("vi").isActive(true).build()));
        Poi request = validPoi(); request.setStatus("PUBLISHED");
        doThrow(new IllegalArgumentException("Missing default audio"))
                .when(pipeline).requireDefaultAudioForPublication(request, request);
        assertThrows(IllegalArgumentException.class, () -> service.saveOrUpdatePoi(request));
        verify(pois, never()).saveAndFlush(any());
        verify(pipeline, never()).enqueueSource(any());
    }
}
