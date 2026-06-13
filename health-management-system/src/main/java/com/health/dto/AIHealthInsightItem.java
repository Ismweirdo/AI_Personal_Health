package com.health.dto;

import lombok.Data;

@Data
public class AIHealthInsightItem {

    private String id;
    private String type;
    private String severity;
    private String title;
    private String summary;
    private String actionSuggestion;
    private String relatedMetric;
    private Double value;
    private String unit;
}
