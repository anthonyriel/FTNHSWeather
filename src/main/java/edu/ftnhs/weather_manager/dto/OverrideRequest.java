package edu.ftnhs.weather_manager.dto;

public class OverrideRequest {
    private String mode;
    private String reason;
    private String notes;
    private int durationMinutes;

    public OverrideRequest() {}

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
}