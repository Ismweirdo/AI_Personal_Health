package com.health.service;

import com.health.dto.AIActionDraftResponse;
import com.health.dto.HealthGoalResponse;
import com.health.dto.ReminderRuleResponse;

import java.util.Map;

public interface AIAssistantActionService {

    AIActionDraftResponse generateDraft(Long userId, String instruction);

    Map<String, Object> executeAction(Long userId, String instruction);

    HealthGoalResponse createGoalFromDraft(Long userId, String instruction);

    ReminderRuleResponse createReminderFromDraft(Long userId, String instruction);
}
