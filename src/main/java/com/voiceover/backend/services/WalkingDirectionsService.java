package com.voiceover.backend.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voiceover.backend.entities.Poi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Walking routes only: the default server actually runs OSRM's foot profile. */
@Service
public class WalkingDirectionsService {
    private final String baseUrl;
    private final long minIntervalMillis;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private long lastRequestNanos;
    private final Map<String, CachedRoute> cache = new LinkedHashMap<>();

    public WalkingDirectionsService(
            @Value("${app.routing.walking-url:https://routing.openstreetmap.de/routed-foot/route/v1/foot}") String baseUrl,
            @Value("${app.routing.min-interval-ms:1100}") long minIntervalMillis) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.minIntervalMillis = Math.max(0, minIntervalMillis);
    }

    // Serialize requests to respect the public server's maximum of one request/second.
    public synchronized WalkingRoute calculate(List<Poi> pois) {
        String coordinates = pois.stream()
                .map(poi -> poi.getLongitude().toPlainString() + "," + poi.getLatitude().toPlainString())
                .collect(Collectors.joining(";"));
        CachedRoute cached = cache.get(coordinates);
        if (cached != null && System.nanoTime() - cached.createdNanos() < Duration.ofMinutes(5).toNanos()) {
            return cached.route();
        }
        try {
            long waitNanos = TimeUnit.MILLISECONDS.toNanos(minIntervalMillis) - (System.nanoTime() - lastRequestNanos);
            if (waitNanos > 0) TimeUnit.NANOSECONDS.sleep(waitNanos);
            lastRequestNanos = System.nanoTime();
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/" + coordinates
                            + "?overview=full&geometries=geojson&steps=false&alternatives=false"))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "VoiceMap/1.0 (walking route planner)")
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());
            if (root == null) throw new IOException("Empty routing response");
            String code = root.path("code").asText();
            if ("NoRoute".equals(code) || "NoSegment".equals(code)) {
                throw new NoWalkingRouteException();
            }
            if (response.statusCode() != 200 || !"Ok".equals(code)) throw new IOException("Invalid routing response");
            JsonNode route = root.path("routes").path(0);
            double distance = route.path("distance").asDouble(Double.NaN);
            double duration = route.path("duration").asDouble(Double.NaN);
            JsonNode geometry = route.path("geometry");
            JsonNode points = geometry.path("coordinates");
            JsonNode routeLegs = route.path("legs");
            if (!Double.isFinite(distance) || distance < 0 || !Double.isFinite(duration) || duration < 0
                    || duration > Integer.MAX_VALUE || !"LineString".equals(geometry.path("type").asText())
                    || !points.isArray() || points.size() < 2 || !routeLegs.isArray() || routeLegs.size() != pois.size() - 1) {
                throw new IOException("Incomplete walking route");
            }
            List<List<Double>> line = new ArrayList<>();
            for (JsonNode point : points) {
                double lng = point.path(0).asDouble(Double.NaN);
                double lat = point.path(1).asDouble(Double.NaN);
                if (!Double.isFinite(lng) || !Double.isFinite(lat) || Math.abs(lng) > 180 || Math.abs(lat) > 90) {
                    throw new IOException("Invalid route coordinates");
                }
                line.add(List.of(lng, lat));
            }
            List<Map<String, Object>> legs = new ArrayList<>();
            for (JsonNode leg : routeLegs) {
                double legDistance = leg.path("distance").asDouble(Double.NaN);
                double legDuration = leg.path("duration").asDouble(Double.NaN);
                if (!Double.isFinite(legDistance) || legDistance < 0 || !Double.isFinite(legDuration) || legDuration < 0) {
                    throw new IOException("Invalid route leg");
                }
                legs.add(Map.of("distanceMeters", Math.round(legDistance), "durationSeconds", (long) Math.ceil(legDuration)));
            }
            WalkingRoute result = new WalkingRoute(Math.round(distance), (int) Math.ceil(duration),
                    Map.of("type", "LineString", "coordinates", line), legs);
            if (cache.size() >= 128) cache.remove(cache.keySet().iterator().next());
            cache.put(coordinates, new CachedRoute(System.nanoTime(), result));
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Tính đường đi bộ bị gián đoạn. Vui lòng thử lại.");
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Dịch vụ tính đường đi bộ tạm thời không khả dụng. Vui lòng thử lại.");
        }
    }

    public record WalkingRoute(long distanceMeters, int durationSeconds,
                               Map<String, Object> geometry, List<Map<String, Object>> legs) {}
    private record CachedRoute(long createdNanos, WalkingRoute route) {}

    public static class NoWalkingRouteException extends IllegalStateException {
        public NoWalkingRouteException() {
            super("Không tìm được đường đi bộ phù hợp. Hãy thay đổi danh sách hoặc thứ tự các điểm tham quan.");
        }
    }
}
