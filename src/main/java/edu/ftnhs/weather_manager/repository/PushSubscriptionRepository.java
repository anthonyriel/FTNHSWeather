package edu.ftnhs.weather_manager.repository;

import edu.ftnhs.weather_manager.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {
    
    boolean existsByEndpoint(String endpoint);
    
    @Transactional
    @Modifying
    void deleteByEndpoint(String endpoint);
}