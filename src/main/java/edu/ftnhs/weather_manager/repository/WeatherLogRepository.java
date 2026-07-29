package edu.ftnhs.weather_manager.repository;

import edu.ftnhs.weather_manager.entity.WeatherLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WeatherLogRepository extends JpaRepository<WeatherLog, UUID> {
    
    WeatherLog findTopByOrderByTimestampDesc();
    
    // Fetch all logs ordered by timestamp descending for history view
    List<WeatherLog> findAllByOrderByTimestampDesc();
}