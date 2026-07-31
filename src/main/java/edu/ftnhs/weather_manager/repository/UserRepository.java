package edu.ftnhs.weather_manager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import edu.ftnhs.weather_manager.entity.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsernameAndRole(String username, String role);
    
    // Added this method to allow safe lookup by username alone
    Optional<User> findByUsername(String username);

    // Added to fetch only active users (hiding soft-deleted accounts)
    List<User> findByIsActiveTrue();

    // Added for Task 2.3: Soft Delete
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.isActive = false WHERE u.id = :id")
    void softDeleteUser(@Param("id") UUID id);
}