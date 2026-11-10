FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw -B dependency:go-offline

COPY src src
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Môi trường Python chạy Edge-TTS
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       python3 python3-venv ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY requirements-edge-tts.txt .
RUN python3 -m venv /opt/edge-tts \
    && /opt/edge-tts/bin/pip install --no-cache-dir \
       -r requirements-edge-tts.txt

COPY scripts/edge_tts_worker.py scripts/edge_tts_worker.py
COPY --from=build /workspace/target/*.jar app.jar

ENV TTS_PROVIDER=edge \
    EDGE_TTS_PYTHON=/opt/edge-tts/bin/python \
    EDGE_TTS_WORKER=/app/scripts/edge_tts_worker.py \
    AUDIO_STORAGE=/app/storage/audio \
    IMAGE_STORAGE=/app/storage/images

RUN mkdir -p /app/storage/audio /app/storage/images

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]