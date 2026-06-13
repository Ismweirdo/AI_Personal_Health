package com.health.dto;

import lombok.Data;

@Data
public class HealthMetricReportItem {

    private String type;
    private String label;
    private String unit;
    private String aggregationMode;
    private Integer recordCount;
    private Integer activeDays;
    private Double summaryValue;
    private Double latestValue;
    private Double averageValue;
    private Double minValue;
    private Double maxValue;
    private String trend;
    private String status;
}
