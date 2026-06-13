package com.health.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class HealthDataRequest {

    @NotBlank(message = "数据类型不能为空")
    private String type;

    @NotNull(message = "数据值不能为空")
    private Double value;

    private String unit;
    private String recordDate;
    private String notes;
}
