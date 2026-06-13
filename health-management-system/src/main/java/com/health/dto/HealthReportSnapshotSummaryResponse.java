package com.health.dto;

import lombok.Data;

@Data
public class HealthReportSnapshotSummaryResponse {

    private Long id;
    private String period;
    private String startDate;
    private String endDate;
    private String generatedAt;
    private Integer metricCount;
    private Integer goalCount;
}
