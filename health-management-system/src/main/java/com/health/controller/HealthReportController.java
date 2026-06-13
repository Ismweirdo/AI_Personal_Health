package com.health.controller;

import com.health.common.Result;
import com.health.dto.HealthReportSnapshotSummaryResponse;
import com.health.dto.HealthReportResponse;
import com.health.service.HealthReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/reports")
@Tag(name = "健康报告", description = "健康周报和月报接口")
public class HealthReportController {

    @Autowired
    private HealthReportService healthReportService;

    @Operation(summary = "生成健康报告")
    @GetMapping
    public Result<HealthReportResponse> generateReport(@RequestParam(defaultValue = "weekly") String period) {
        return Result.success(healthReportService.generateReport(period));
    }

    @Operation(summary = "查询报告历史快照")
    @GetMapping("/history")
    public Result<List<HealthReportSnapshotSummaryResponse>> getReportSnapshots(
            @RequestParam(defaultValue = "weekly") String period) {
        return Result.success(healthReportService.getReportSnapshots(period));
    }

    @Operation(summary = "读取报告历史快照详情")
    @GetMapping("/history/{snapshotId}")
    public Result<HealthReportResponse> getReportSnapshot(@PathVariable Long snapshotId) {
        return Result.success(healthReportService.getReportSnapshot(snapshotId));
    }
}
