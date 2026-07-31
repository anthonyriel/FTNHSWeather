package edu.ftnhs.weather_manager.repository;

import edu.ftnhs.weather_manager.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
}