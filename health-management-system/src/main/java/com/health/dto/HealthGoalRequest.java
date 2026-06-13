package com.health.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HealthGoalRequest {

    @NotBlank(message = "目标类型不能为空")
    private String type;

    @NotNull(message = "目标值不能为空")
    @Min(value = 1, message = "目标值必须大于0")
    private Double targetValue;

    private String unit;

    @NotBlank(message = "目标周期不能为空")
    private String period;

    private Boolean enabled = true;
}
