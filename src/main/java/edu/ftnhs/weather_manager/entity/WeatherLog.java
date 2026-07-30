package edu.ftnhs.weather_manager.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "weather_logs")
public class WeatherLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private OffsetDateTime timestamp;
    private String location;
    private Double temperature;
    
    @Column(name = "precipitation_mm")
    private Double precipitationMm;
    
    @Column(name = "wind_speed")
    private Double windSpeed;
    
    private Double humidity;
    
    @Column(name = "cloud_cover")
    private Integer cloudCover;
    
    private Double pressure;
    private Double visibility;
    
    @Column(name = "uv_index")
    private Double uvIndex;
    
    // Fixed: Matches the database column name 'rainfall_probability'
    @Column(name = "rainfall_probability")
    private Double precipitationProbability;
    
    private String source;
    
    @Column(name = "weather_condition")
    private String weatherCondition;

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public OffsetDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(OffsetDateTime timestamp) { this.timestamp = timestamp; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Double getPrecipitationMm() { return precipitationMm; }
    public void setPrecipitationMm(Double precipitationMm) { this.precipitationMm = precipitationMm; }

    public Double getWindSpeed() { return windSpeed; }
    public void setWindSpeed(Double windSpeed) { this.windSpeed = windSpeed; }

    public Double getHumidity() { return humidity; }
    public void setHumidity(Double humidity) { this.humidity = humidity; }

    public Integer getCloudCover() { return cloudCover; }
    public void setCloudCover(Integer cloudCover) { this.cloudCover = cloudCover; }

    public Double getPressure() { return pressure; }
    public void setPressure(Double pressure) { this.pressure = pressure; }

    public Double getVisibility() { return visibility; }
    public void setVisibility(Double visibility) { this.visibility = visibility; }

    public Double getUvIndex() { return uvIndex; }
    public void setUvIndex(Double uvIndex) { this.uvIndex = uvIndex; }

    public Double getPrecipitationProbability() { return precipitationProbability; }
    public void setPrecipitationProbability(Double precipitationProbability) { this.precipitationProbability = precipitationProbability; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getWeatherCondition() { return weatherCondition; }
    public void setWeatherCondition(String weatherCondition) { this.weatherCondition = weatherCondition; }
}