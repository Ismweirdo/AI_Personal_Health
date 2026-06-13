package com.health.entity;

import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "health_devices")
public class HealthDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "device_id", nullable = false, unique = true)
    private String deviceId;

    @Column(name = "device_name", nullable = false)
    private String deviceName;

    @Column(name = "device_type", nullable = false)
    private String deviceType;

    @Column(name = "device_model")
    private String deviceModel;

    @Column(name = "manufacturer")
    private String manufacturer;

    @Column(name = "firmware_version")
    private String firmwareVersion;

    @Column(name = "api_key", nullable = false, unique = true)
    private String apiKey;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "last_active")
    private LocalDateTime lastActive;

    @Column(name = "description", length = 500)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}