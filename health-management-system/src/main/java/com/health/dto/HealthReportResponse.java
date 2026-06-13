package com.health.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HealthReportResponse {

    private Long snapshotId;
    private String period;
    private String startDate;
    private String endDate;
    private String generatedAt;
    private String sourceType;
    private String summaryMethod;
    private List<HealthMetricReportItem> metrics = new ArrayList<>();
    private List<HealthGoalResponse> goals = new ArrayList<>();
    private List<String> highlights = new ArrayList<>();
    private List<String> suggestions = new ArrayList<>();
}
