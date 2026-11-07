package com.voiceover.backend.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WikimediaImageService {
    private static final Map<String, String> SEARCH_TERMS = Map.of(
            "Đại học Sài Gòn", "Saigon University",
            "Công viên 23 tháng 9", "23 September Park Ho Chi Minh City",
            "Chợ An Đông", "An Dong Market Ho Chi Minh City",
            "Nhà thờ Chợ Quán", "Cho Quan Church Ho Chi Minh City"
    );
    private static final Map<String, String> DIRECT_IMAGE_URLS = Map.of(
            "Nhà thờ Chợ Quán",
            "https://commons.wikimedia.org/wiki/Special:FilePath/Tong_quan_Cung_thanh_Nha_Tho_Cho_Quan.jpg?width=900"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public String findImageUrl(String poiName) {
        String directImageUrl = DIRECT_IMAGE_URLS.get(poiName);
        if (directImageUrl != null) {
            return directImageUrl;
        }
        String searchTerm = SEARCH_TERMS.getOrDefault(poiName, poiName + " Ho Chi Minh City");
        String query = "https://commons.wikimedia.org/w/api.php?action=query&generator=search"
                + "&gsrsearch=" + URLEncoder.encode(searchTerm, StandardCharsets.UTF_8)
                + "&gsrnamespace=6&gsrlimit=1&prop=imageinfo&iiprop=url"
                + "&iiurlwidth=900&format=json";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(query))
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", "VoiceMap/1.0 (educational project)")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return null;
            }
            JsonNode pages = objectMapper.readTree(response.body()).path("query").path("pages");
            if (!pages.isObject()) {
                return null;
            }
            var pageIterator = pages.elements();
            if (!pageIterator.hasNext()) {
                return null;
            }
            JsonNode imageInfo = pageIterator.next().path("imageinfo").path(0);
            String thumbnailUrl = imageInfo.path("thumburl").asText(null);
            return thumbnailUrl == null || thumbnailUrl.isBlank()
                    ? imageInfo.path("url").asText(null)
                    : thumbnailUrl;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException exception) {
            return null;
        }
    }

    public boolean hasDirectImage(String poiName) {
        return DIRECT_IMAGE_URLS.containsKey(poiName);
    }
}
