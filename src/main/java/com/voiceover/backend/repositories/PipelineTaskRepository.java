package com.voiceover.backend.repositories;
import com.voiceover.backend.entities.PipelineTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PipelineTaskRepository extends JpaRepository<PipelineTask, Long> {
    long countByStatus(String status);
    List<PipelineTask> findByPoiId(Long poiId);
}
