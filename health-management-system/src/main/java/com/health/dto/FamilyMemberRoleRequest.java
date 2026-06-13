package com.health.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class FamilyMemberRoleRequest {

    @NotBlank(message = "成员角色不能为空")
    @Pattern(regexp = "parent|child", message = "成员角色必须为 parent 或 child")
    private String role;
}
