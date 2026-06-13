package com.health.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceRegistrationResponse {

    private String deviceId;
    private String apiKey;
    private String deviceName;
    private String deviceType;
    private String deviceModel;
    private String manufacturer;
    private String firmwareVersion;
    private String status;
    private LocalDateTime createdAt;
    private String message;
}