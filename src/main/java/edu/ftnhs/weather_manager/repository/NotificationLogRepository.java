package edu.ftnhs.weather_manager.repository;

import edu.ftnhs.weather_manager.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    List<NotificationLog> findTop10ByOrderBySentAtDesc();
}