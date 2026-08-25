package edu.ftnhs.weather_manager.repository;

import edu.ftnhs.weather_manager.entity.WeatherLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface WeatherLogRepository extends JpaRepository<WeatherLog, UUID> {
    
    WeatherLog findTopByOrderByTimestampDesc();
    
    // Fetch all logs ordered by timestamp descending for history view
    List<WeatherLog> findAllByOrderByTimestampDesc();

    // Bulk delete older than cutoff to bypass single-row timeouts
    @Modifying
    @Query("DELETE FROM WeatherLog w WHERE w.timestamp < :cutoff")
    void deleteByTimestampBefore(@Param("cutoff") OffsetDateTime cutoff);
}