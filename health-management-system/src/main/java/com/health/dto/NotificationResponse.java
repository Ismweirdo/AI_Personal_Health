package com.health.dto;

import lombok.Data;

@Data
public class NotificationResponse {

    private Long id;
    private Long ruleId;
    private String title;
    private String type;
    private String message;
    private String actionType;
    private Long actionRefId;
    private String actionStatus;
    private String status;
    private String scheduledFor;
    private String readAt;
    private String createdAt;
}
