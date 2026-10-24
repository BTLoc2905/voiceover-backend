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
    private final PipelineService pipeline = mock(PipelineService.class);
    private final PoiService service = new PoiService(pois, mock(TranslationRepository.class), mock(AudioRepository.class),
            languages, mock(RouteStopRepository.class), pipeline, mock(OpenStreetMapPoiService.class),
            mock(WikimediaImageService.class), mock(TranslationReviewService.class), mock(PoiThumbnailService.class));

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
        verify(pois, never()).save(any());
        verifyNoInteractions(pipeline);
    }

    @Test
    void trimsTextNormalizesStatusAndStartsPipelineAfterSavingValidPoi() {
        when(languages.findById("vi")).thenReturn(Optional.of(Language.builder().code("vi").isActive(true).build()));
        when(pois.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Poi saved = service.saveOrUpdatePoi(validPoi());

        assertEquals("Tháp thử nghiệm", saved.getName());
        assertEquals("Nội dung mô tả", saved.getDescriptionSource());
        assertEquals("DRAFT", saved.getStatus());
        verify(pois).save(saved);
        verify(pipeline).triggerFullPipelineForPoi(saved);
    }
}
