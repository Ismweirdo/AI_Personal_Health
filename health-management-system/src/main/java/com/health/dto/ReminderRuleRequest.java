package com.health.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReminderRuleRequest {

    @NotBlank(message = "提醒标题不能为空")
    private String title;

    private String type;

    private String message;

    @NotBlank(message = "提醒频率不能为空")
    private String frequency;

    private String remindTime;

    private String remindDate;

    @Min(value = 1, message = "每周提醒日期必须在1到7之间")
    @Max(value = 7, message = "每周提醒日期必须在1到7之间")
    private Integer weeklyDay;

    private Boolean enabled = true;
}
