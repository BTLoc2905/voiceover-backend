package com.voiceover.backend.repositories;
import com.voiceover.backend.entities.PipelineTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
public interface PipelineTaskRepository extends JpaRepository<PipelineTask, Long> {
    @Query("select t.poiId from PipelineTask t where t.id = :id")
    Optional<Long> findPoiIdById(@Param("id") Long id);
    long countByStatus(String status);
    List<PipelineTask> findByPoiId(Long poiId);
    Optional<PipelineTask> findFirstByPoiIdAndTargetLanguageCodeOrderByIdDesc(Long poiId, String languageCode);
    boolean existsByPoiIdAndTargetLanguageCodeAndStatusIn(Long poiId, String languageCode, Collection<String> statuses);
    boolean existsByPoiIdAndTaskTypeAndStatusIn(Long poiId, String taskType, Collection<String> statuses);
    List<PipelineTask> findByTaskTypeAndStatusIn(String taskType, Collection<String> statuses);
}
