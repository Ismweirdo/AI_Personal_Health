package com.health.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginRequest {
    @NotBlank(message = "账号不能为空")
    @Size(max = 100, message = "账号长度不能超过100")
    private String account; // username, email, or phone

    @NotBlank(message = "密码不能为空")
    @Size(max = 72, message = "密码长度不能超过72")
    private String password;

    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
