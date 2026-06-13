package com.health.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AIHealthGuardianResponse {

    private String generatedAt;
    private String sourceType;
    private boolean cacheHit;
    private String overallStatus;
    private String summary;
    private List<AIHealthInsightItem> items = new ArrayList<>();
}
