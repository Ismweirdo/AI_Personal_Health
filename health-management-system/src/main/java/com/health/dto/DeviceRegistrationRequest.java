package com.health.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class DeviceRegistrationRequest {

    @NotBlank(message = "设备名称不能为空")
    private String deviceName;

    @NotBlank(message = "设备类型不能为空")
    private String deviceType;

    private String deviceModel;

    private String manufacturer;

    private String firmwareVersion;

    private String description;
}