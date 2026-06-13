package com.health.dto;

import lombok.Data;

@Data
public class AIGoalDraft {

    private String type;
    private String typeLabel;
    private Double targetValue;
    private String unit;
    private String period;
    private Boolean enabled = true;
    private Double currentValue;
    private String suggestionReason;
}
