package com.health.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "reminder_rules")
public class ReminderRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 50)
    private String type;

    @Column(length = 500)
    private String message;

    @Column(nullable = false, length = 20)
    private String frequency;

    @Column(name = "remind_time", length = 10)
    private String remindTime;

    @Column(name = "remind_date")
    private LocalDate remindDate;

    @Column(name = "weekly_day")
    private Integer weeklyDay;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "next_trigger_at")
    private LocalDateTime nextTriggerAt;

    @Column(name = "last_triggered_at")
    private LocalDateTime lastTriggeredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
