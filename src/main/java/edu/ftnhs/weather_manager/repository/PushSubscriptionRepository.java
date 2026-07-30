package edu.ftnhs.weather_manager.repository;

import edu.ftnhs.weather_manager.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {
    boolean existsByEndpoint(String endpoint);
}