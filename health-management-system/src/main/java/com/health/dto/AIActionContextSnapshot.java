package com.health.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AIActionContextSnapshot {

    private int healthRecordCount;
    private int activeGoalCount;
    private int reminderCount;
    private int enabledReminderCount;
    private int unreadNotificationCount;
    private int deviceCount;
    private List<String> latestMetricSummaries = new ArrayList<>();
}
