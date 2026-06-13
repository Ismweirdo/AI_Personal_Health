package com.health.service;

import com.health.dto.HealthGoalRequest;
import com.health.dto.HealthGoalResponse;

import java.util.List;

public interface HealthGoalService {

    HealthGoalResponse createGoal(HealthGoalRequest request);

    HealthGoalResponse createGoal(HealthGoalRequest request, Long targetUserId);

    HealthGoalResponse updateGoal(Long id, HealthGoalRequest request);

    HealthGoalResponse updateGoal(Long id, HealthGoalRequest request, Long targetUserId);

    List<HealthGoalResponse> getGoals(Boolean enabledOnly);

    List<HealthGoalResponse> getGoals(Boolean enabledOnly, Long targetUserId);

    void deleteGoal(Long id);

    void deleteGoal(Long id, Long targetUserId);
}
