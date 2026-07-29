package edu.ftnhs.weather_manager.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "learning_status_logs")
public class LearningStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_date")
    private LocalDate targetDate;

    private String status;

    @Column(name = "automated_by_system")
    private Boolean automatedBySystem;

    private String reason;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public LearningStatusLog() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getTargetDate() { return targetDate; }
    public void setTargetDate(LocalDate targetDate) { this.targetDate = targetDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getAutomatedBySystem() { return automatedBySystem; }
    public void setAutomatedBySystem(Boolean automatedBySystem) { this.automatedBySystem = automatedBySystem; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}