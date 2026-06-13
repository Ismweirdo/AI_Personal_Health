package com.health.controller;

import com.health.common.Result;
import com.health.dto.HealthDataImportResult;
import com.health.dto.HealthDataRequest;
import com.health.dto.HealthDataResponse;
import com.health.service.HealthDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/health")
@Tag(name = "健康数据管理", description = "健康数据相关接口")
public class HealthDataController {

    @Autowired
    private HealthDataService healthDataService;

    @Operation(summary = "添加健康数据")
    @PostMapping("/data")
    public Result<HealthDataResponse> addHealthData(
            @Valid @RequestBody HealthDataRequest request,
            @RequestParam(required = false) Long targetUserId) {
        return Result.success(healthDataService.addHealthData(request, targetUserId));
    }

    @Operation(summary = "更新健康数据")
    @PutMapping("/data/{id}")
    public Result<HealthDataResponse> updateHealthData(
            @PathVariable Long id,
            @Valid @RequestBody HealthDataRequest request,
            @RequestParam(required = false) Long targetUserId) {
        return Result.success(healthDataService.updateHealthData(id, request, targetUserId));
    }

    @Operation(summary = "获取用户健康数据列表")
    @GetMapping("/data")
    public Result<List<HealthDataResponse>> getHealthDataList(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long targetUserId) {
        return Result.success(healthDataService.getHealthDataList(type, startDate, endDate, targetUserId));
    }

    @Operation(summary = "获取健康数据趋势")
    @GetMapping("/trend")
    public Result<List<HealthDataResponse>> getHealthDataTrend(
            @RequestParam String type,
            @RequestParam String period,
            @RequestParam(required = false) Long targetUserId) {
        return Result.success(healthDataService.getHealthDataTrend(type, period, targetUserId));
    }

    @Operation(summary = "批量导入健康数据")
    @PostMapping("/data/import")
    public Result<HealthDataImportResult> importHealthData(
            @RequestBody List<HealthDataRequest> requests,
            @RequestParam(required = false) Long targetUserId) {
        return Result.success(healthDataService.importHealthData(requests, targetUserId));
    }

    @Operation(summary = "导出健康数据为CSV")
    @GetMapping("/data/export")
    public ResponseEntity<String> exportHealthData(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long targetUserId) {
        String content = healthDataService.exportHealthData(type, startDate, endDate, targetUserId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=health-data.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(content);
    }

    @Operation(summary = "下载导入模板")
    @GetMapping("/data/import-template")
    public ResponseEntity<String> getImportTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=health-data-template.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(healthDataService.getImportTemplate());
    }

    @Operation(summary = "删除健康数据")
    @DeleteMapping("/data/{id}")
    public Result<Void> deleteHealthData(
            @PathVariable Long id,
            @RequestParam(required = false) Long targetUserId) {
        healthDataService.deleteHealthData(id, targetUserId);
        return Result.success();
    }
}
