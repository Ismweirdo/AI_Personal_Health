package com.health.controller;

import com.health.common.Result;
import com.health.dto.NotificationResponse;
import com.health.dto.ReminderRuleRequest;
import com.health.dto.ReminderRuleResponse;
import com.health.service.ReminderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/reminders")
@Tag(name = "提醒通知管理", description = "提醒规则与通知中心接口")
public class ReminderController {

    @Autowired
    private ReminderService reminderService;

    @Operation(summary = "创建提醒规则")
    @PostMapping("/rules")
    public Result<ReminderRuleResponse> createRule(
            @Valid @RequestBody ReminderRuleRequest request,
            @RequestParam(required = false) Long targetUserId) {
        return Result.success(reminderService.createRule(request, targetUserId));
    }

    @Operation(summary = "更新提醒规则")
    @PutMapping("/rules/{id}")
    public Result<ReminderRuleResponse> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody ReminderRuleRequest request,
            @RequestParam(required = false) Long targetUserId) {
        return Result.success(reminderService.updateRule(id, request, targetUserId));
    }

    @Operation(summary = "启用或停用提醒规则")
    @PutMapping("/rules/{id}/enabled")
    public Result<ReminderRuleResponse> toggleRuleEnabled(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> request,
            @RequestParam(required = false) Long targetUserId) {
        return Result.success(reminderService.toggleRuleEnabled(id, request.get("enabled"), targetUserId));
    }

    @Operation(summary = "获取提醒规则列表")
    @GetMapping("/rules")
    public Result<List<ReminderRuleResponse>> getRules(@RequestParam(required = false) Long targetUserId) {
        return Result.success(reminderService.getRules(targetUserId));
    }

    @Operation(summary = "删除提醒规则")
    @DeleteMapping("/rules/{id}")
    public Result<Void> deleteRule(
            @PathVariable Long id,
            @RequestParam(required = false) Long targetUserId) {
        reminderService.deleteRule(id, targetUserId);
        return Result.success();
    }

    @Operation(summary = "获取通知列表")
    @GetMapping("/notifications")
    public Result<List<NotificationResponse>> getNotifications() {
        return Result.success(reminderService.getNotifications());
    }

    @Operation(summary = "标记通知已读")
    @PutMapping("/notifications/{id}/read")
    public Result<NotificationResponse> markNotificationRead(@PathVariable Long id) {
        return Result.success(reminderService.markNotificationRead(id));
    }

    @Operation(summary = "删除通知")
    @DeleteMapping("/notifications/{id}")
    public Result<Void> deleteNotification(@PathVariable Long id) {
        reminderService.deleteNotification(id);
        return Result.success();
    }
}
