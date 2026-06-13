package com.health.dto;

import lombok.Data;

@Data
public class ReminderRuleResponse {

    private Long id;
    private String title;
    private String type;
    private String message;
    private String frequency;
    private String remindTime;
    private String remindDate;
    private Integer weeklyDay;
    private Boolean enabled;
    private String nextTriggerAt;
    private String lastTriggeredAt;
    private String createdAt;
}
