package com.health.dto;

import lombok.Data;

@Data
public class AIReminderDraft {

    private String title;
    private String type;
    private String message;
    private String frequency;
    private String remindTime;
    private String remindDate;
    private Integer weeklyDay;
    private Boolean enabled = true;
    private String suggestionReason;
}
