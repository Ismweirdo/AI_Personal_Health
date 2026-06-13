package com.health.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FamilyTransferCreatorRequest {

    @NotNull(message = "新创建者用户ID不能为空")
    private Long newCreatorUserId;
}
