package edu.ftnhs.weather_manager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import edu.ftnhs.weather_manager.entity.LearningStatusLog;

import java.time.LocalDate;
import java.util.Optional;

public interface LearningStatusLogRepository extends JpaRepository<LearningStatusLog, Long> {
    
    Optional<LearningStatusLog> findTopByTargetDateOrderByCreatedAtDesc(LocalDate targetDate);

    @Transactional
    void deleteByTargetDate(LocalDate targetDate);

    // Auto-delete learning status logs older than the specified target date cutoff
    void deleteByTargetDateBefore(LocalDate cutoffDate);
}