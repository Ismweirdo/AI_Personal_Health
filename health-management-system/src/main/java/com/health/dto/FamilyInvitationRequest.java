package com.health.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class FamilyInvitationRequest {

    @NotBlank(message = "被邀请人手机号不能为空")
    private String inviteePhone;

    @NotBlank(message = "被邀请人角色不能为空")
    @Pattern(regexp = "parent|child", message = "被邀请人角色必须为 parent 或 child")
    private String inviteeRole = "child";

    @Min(value = 1, message = "邀请有效期不能少于1天")
    @Max(value = 30, message = "邀请有效期不能超过30天")
    private Integer expiresInDays = 7;
}
