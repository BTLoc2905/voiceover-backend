package com.voiceover.backend.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voiceover.backend.entities.Poi;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class RealContentService {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    @Value("${openai.api.key:}")
    private String openAiApiKey;

    @Value("${app.image.storage:storage/images}")
    private String imageStorage;

    public Poi createPoiWithRealImage(String name, String description, BigDecimal latitude,
                                      BigDecimal longitude, Integer radiusM) {
        try {
            byte[] imageBytes;
            if (openAiApiKey.isBlank()) {
                imageBytes = createLocalPlaceholder(name);
            } else {
                String prompt = "Create a realistic travel guide image for the landmark named "
                        + name + ". Description: " + description
                        + ". No text, no logo, no watermark.";
                String body = objectMapper.createObjectNode()
                        .put("model", "gpt-image-1")
                        .put("prompt", prompt)
                        .put("size", "1024x1024")
                        .toString();
                HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/images/generations"))
                        .timeout(Duration.ofSeconds(60))
                        .header("Authorization", "Bearer " + openAiApiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) {
                    throw new IllegalStateException("OpenAI Images API trả về HTTP " + response.statusCode()
                            + ": " + response.body());
                }
                JsonNode data = objectMapper.readTree(response.body()).path("data").path(0);
                String imageUrl = data.path("url").asText(null);
                if (imageUrl != null && !imageUrl.isBlank()) {
                    imageBytes = downloadImage(imageUrl);
                } else {
                    String encodedImage = data.path("b64_json").asText(null);
                    if (encodedImage == null || encodedImage.isBlank()) {
                        throw new IllegalStateException("OpenAI không trả về URL hoặc dữ liệu base64 của ảnh.");
                    }
                    try {
                        imageBytes = Base64.getDecoder().decode(encodedImage);
                    } catch (IllegalArgumentException exception) {
                        throw new IllegalStateException("OpenAI trả về dữ liệu ảnh base64 không hợp lệ.", exception);
                    }
                }
            }
            Path directory = Path.of(imageStorage);
            Files.createDirectories(directory);
            String fileExtension = openAiApiKey.isBlank() ? "svg" : "png";
            String fileName = "poi-" + System.currentTimeMillis() + "." + fileExtension;
            Files.write(directory.resolve(fileName), imageBytes);
            return Poi.builder()
                    .name(name)
                    .descriptionSource(description)
                    .latitude(latitude)
                    .longitude(longitude)
                    .radiusM(radiusM == null ? 50 : radiusM)
                    .thumbnailUrl("/images/" + fileName)
                    .sourceLanguageCode("vi")
                    .status("PUBLISHED")
                    .build();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Yêu cầu tạo ảnh bị gián đoạn.", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể tạo ảnh POI thật.", exception);
        }
    }

    private byte[] createLocalPlaceholder(String name) {
        String safeName = name == null ? "POI" : name.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;");
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="1024" height="640" viewBox="0 0 1024 640">
                  <defs><linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
                    <stop offset="0" stop-color="#193d5c"/><stop offset="1" stop-color="#d27d4f"/>
                  </linearGradient></defs>
                  <rect width="1024" height="640" fill="url(#bg)"/>
                  <circle cx="512" cy="260" r="120" fill="#ffffff" opacity=".16"/>
                  <path d="M512 120 570 250H454z" fill="#fff" opacity=".8"/>
                  <text x="512" y="450" fill="#fff" font-family="Arial,sans-serif" font-size="42"
                        text-anchor="middle">%s</text>
                  <text x="512" y="500" fill="#fff" opacity=".8" font-family="Arial,sans-serif"
                        font-size="24" text-anchor="middle">VoiceMap</text>
                </svg>
                """.formatted(safeName);
        return svg.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private byte[] downloadImage(String imageUrl) throws IOException, InterruptedException {
        HttpRequest imageRequest = HttpRequest.newBuilder(URI.create(imageUrl))
                .timeout(Duration.ofSeconds(30)).GET().build();
        HttpResponse<byte[]> imageResponse = httpClient.send(imageRequest, HttpResponse.BodyHandlers.ofByteArray());
        if (imageResponse.statusCode() / 100 != 2) {
            throw new IllegalStateException("Không tải được ảnh từ OpenAI. HTTP " + imageResponse.statusCode());
        }
        return imageResponse.body();
    }
}
