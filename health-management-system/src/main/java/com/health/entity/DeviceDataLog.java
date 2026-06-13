package com.health.entity;

import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "device_data_logs")
public class DeviceDataLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "data_type", nullable = false)
    private String dataType;

    @Column(name = "data_value", nullable = false)
    private Double dataValue;

    @Column(name = "unit")
    private String unit;

    @Column(name = "record_date", nullable = false)
    private LocalDateTime recordDate;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "request_id")
    private String requestId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}