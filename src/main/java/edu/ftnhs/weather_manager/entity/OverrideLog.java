package edu.ftnhs.weather_manager.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "override_logs")
public class OverrideLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "previous_mode", nullable = false)
    private String previousMode;

    @Column(name = "new_mode", nullable = false)
    private String newMode;

    @Column(nullable = false)
    private String reason;

    private String notes;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public OverrideLog() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getPreviousMode() { return previousMode; }
    public void setPreviousMode(String previousMode) { this.previousMode = previousMode; }

    public String getNewMode() { return newMode; }
    public void setNewMode(String newMode) { this.newMode = newMode; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}