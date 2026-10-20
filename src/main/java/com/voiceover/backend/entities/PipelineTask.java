package com.voiceover.backend.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pipeline_task")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PipelineTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pipeline_task_id")
    private Long id;

    @Column(name = "poi_id", nullable = false)
    private Long poiId;

    @Column(name = "target_language_code", length = 10, nullable = false)
    private String targetLanguageCode;

    @Column(name = "task_type", length = 30, nullable = false)
    private String taskType; // TRANSLATE_TTS | TTS_ONLY

    @Column(nullable = false, length = 20)
    private String status; // PENDING | PROCESSING | COMPLETED | FAILED

    @Column(name = "retry_count")
    private int retryCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}