package com.health.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class SendSmsCodeRequest {
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "验证码用途不能为空")
    @Pattern(regexp = "^(register|login)$", message = "验证码用途不正确")
    private String purpose;

    @NotBlank(message = "人机验证令牌不能为空")
    private String captchaToken;

    @NotBlank(message = "请选择正确图像")
    private String selectedOptionId;

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getCaptchaToken() { return captchaToken; }
    public void setCaptchaToken(String captchaToken) { this.captchaToken = captchaToken; }
    public String getSelectedOptionId() { return selectedOptionId; }
    public void setSelectedOptionId(String selectedOptionId) { this.selectedOptionId = selectedOptionId; }
}
