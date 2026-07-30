package edu.ftnhs.weather_manager.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime; // Changed from LocalDateTime
import java.util.UUID;

@Entity
@Table(name = "weather_logs")
public class WeatherLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private OffsetDateTime timestamp; // Changed type
    private String location;
    
    @Column(name = "precipitation_mm")
    private double precipitationMm;
    
    private String weatherCondition;
    private Double temperature;

    @Column(name = "wind_speed")
    private Double windSpeed;

    @Column(name = "humidity")
    private Double humidity;

    @Column(name = "cloud_cover")
    private Integer cloudCover;

    public WeatherLog() {}

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public OffsetDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(OffsetDateTime timestamp) { this.timestamp = timestamp; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public double getPrecipitationMm() { return precipitationMm; }
    public void setPrecipitationMm(double precipitationMm) { this.precipitationMm = precipitationMm; }

    public String getWeatherCondition() { return weatherCondition; }
    public void setWeatherCondition(String weatherCondition) { this.weatherCondition = weatherCondition; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Double getWindSpeed() { return windSpeed; }
    public void setWindSpeed(Double windSpeed) { this.windSpeed = windSpeed; }

    public Double getHumidity() { return humidity; }
    public void setHumidity(Double humidity) { this.humidity = humidity; }

    public Integer getCloudCover() { return cloudCover; }
    public void setCloudCover(Integer cloudCover) { this.cloudCover = cloudCover; }
}