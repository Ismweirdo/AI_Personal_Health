package com.health.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "health_report_snapshot")
public class HealthReportSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "period", nullable = false, length = 20)
    private String period;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "metrics_json", columnDefinition = "LONGTEXT", nullable = false)
    private String metricsJson;

    @Column(name = "goals_json", columnDefinition = "LONGTEXT", nullable = false)
    private String goalsJson;

    @Column(name = "highlights_json", columnDefinition = "LONGTEXT", nullable = false)
    private String highlightsJson;

    @Column(name = "suggestions_json", columnDefinition = "LONGTEXT", nullable = false)
    private String suggestionsJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
