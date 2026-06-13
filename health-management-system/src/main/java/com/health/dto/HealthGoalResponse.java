package com.health.dto;

import lombok.Data;

@Data
public class HealthGoalResponse {

    private Long id;
    private String type;
    private Double targetValue;
    private String unit;
    private String period;
    private Boolean enabled;
    private Double currentValue;
    private Double progress;
    private Double remainingValue;
    private String createdAt;
    private String updatedAt;

    public String getTypeLabel() {
        if (type == null || type.isBlank()) {
            return type;
        }

        return switch (type.trim()) {
            case "steps" -> "步数";
            case "heart_rate" -> "心率";
            case "sleep" -> "睡眠";
            case "weight" -> "体重";
            case "blood_pressure" -> "血压";
            case "blood_sugar" -> "血糖";
            case "diet" -> "饮食";
            case "exercise" -> "运动";
            case "mood" -> "情绪";
            default -> type;
        };
    }
}
