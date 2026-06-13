package com.health.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "notification_records")
public class NotificationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 50)
    private String type;

    @Column(length = 500)
    private String message;

    @Column(name = "action_type", length = 50)
    private String actionType;

    @Column(name = "action_ref_id")
    private Long actionRefId;

    @Column(name = "action_status", length = 20)
    private String actionStatus;

    @Column(nullable = false, length = 20)
    private String status = "unread";

    @Column(name = "scheduled_for")
    private LocalDateTime scheduledFor;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
