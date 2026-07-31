package edu.ftnhs.weather_manager.repository;

import edu.ftnhs.weather_manager.entity.OverrideLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OverrideLogRepository extends JpaRepository<OverrideLog, Long> {
}