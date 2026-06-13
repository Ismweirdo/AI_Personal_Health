package com.health.service;

import com.health.dto.NotificationResponse;
import com.health.dto.ReminderRuleRequest;
import com.health.dto.ReminderRuleResponse;

import java.util.List;

public interface ReminderService {

    ReminderRuleResponse createRule(ReminderRuleRequest request);

    ReminderRuleResponse createRule(ReminderRuleRequest request, Long targetUserId);

    ReminderRuleResponse updateRule(Long id, ReminderRuleRequest request);

    ReminderRuleResponse updateRule(Long id, ReminderRuleRequest request, Long targetUserId);

    ReminderRuleResponse toggleRuleEnabled(Long id, Boolean enabled);

    ReminderRuleResponse toggleRuleEnabled(Long id, Boolean enabled, Long targetUserId);

    List<ReminderRuleResponse> getRules();

    List<ReminderRuleResponse> getRules(Long targetUserId);

    void deleteRule(Long id);

    void deleteRule(Long id, Long targetUserId);

    List<NotificationResponse> getNotifications();

    NotificationResponse markNotificationRead(Long id);

    void deleteNotification(Long id);
}
