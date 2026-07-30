package edu.ftnhs.weather_manager.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import edu.ftnhs.weather_manager.entity.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsernameAndRole(String username, String role);
    
    // Added this method to allow safe lookup by username alone
    Optional<User> findByUsername(String username);
}