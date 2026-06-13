package com.health.service;

import com.health.dto.HealthReportSnapshotSummaryResponse;
import com.health.dto.HealthReportResponse;

import java.util.List;

public interface HealthReportService {

    HealthReportResponse generateReport(String period);

    List<HealthReportSnapshotSummaryResponse> getReportSnapshots(String period);

    HealthReportResponse getReportSnapshot(Long snapshotId);
}
