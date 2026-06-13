package com.health.controller;

import com.health.common.Result;
import com.health.dto.HealthGoalRequest;
import com.health.dto.HealthGoalResponse;
import com.health.service.HealthGoalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/goals")
@Tag(name = "健康目标管理", description = "健康目标相关接口")
public class HealthGoalController {

    @Autowired
    private HealthGoalService healthGoalService;

    @Operation(summary = "创建健康目标")
    @PostMapping
    public Result<HealthGoalResponse> createGoal(
            @Valid @RequestBody HealthGoalRequest request,
            @RequestParam(required = false) Long targetUserId) {
        return Result.success(healthGoalService.createGoal(request, targetUserId));
    }

    @Operation(summary = "更新健康目标")
    @PutMapping("/{id}")
    public Result<HealthGoalResponse> updateGoal(
            @PathVariable Long id,
            @Valid @RequestBody HealthGoalRequest request,
            @RequestParam(required = false) Long targetUserId) {
        return Result.success(healthGoalService.updateGoal(id, request, targetUserId));
    }

    @Operation(summary = "获取健康目标列表")
    @GetMapping
    public Result<List<HealthGoalResponse>> getGoals(
            @RequestParam(required = false) Boolean enabledOnly,
            @RequestParam(required = false) Long targetUserId) {
        return Result.success(healthGoalService.getGoals(enabledOnly, targetUserId));
    }

    @Operation(summary = "删除健康目标")
    @DeleteMapping("/{id}")
    public Result<Void> deleteGoal(
            @PathVariable Long id,
            @RequestParam(required = false) Long targetUserId) {
        healthGoalService.deleteGoal(id, targetUserId);
        return Result.success();
    }
}
