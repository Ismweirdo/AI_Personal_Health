package com.health.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AIActionDraftRequest {

    @NotBlank(message = "操作指令不能为空")
    private String instruction;
}
