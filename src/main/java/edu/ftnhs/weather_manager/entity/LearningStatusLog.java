package edu.ftnhs.weather_manager.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime; // Changed from LocalDateTime

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
    private OffsetDateTime createdAt; // Changed type

    @Column(name = "confidence_score")
    private BigDecimal confidenceScore;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(name = "rainfall_used")
    private Double rainfallUsed;

    @Column(name = "wind_used")
    private Double windUsed;

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

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public BigDecimal getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(BigDecimal confidenceScore) { this.confidenceScore = confidenceScore; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public Double getRainfallUsed() { return rainfallUsed; }
    public void setRainfallUsed(Double rainfallUsed) { this.rainfallUsed = rainfallUsed; }

    public Double getWindUsed() { return windUsed; }
    public void setWindUsed(Double windUsed) { this.windUsed = windUsed; }
}