package com.health.controller;

import com.health.annotation.Log;
import com.health.common.Result;
import com.health.dto.AuthResponse;
import com.health.dto.CaptchaChallengeResponse;
import com.health.dto.LoginRequest;
import com.health.dto.PhoneCodeLoginRequest;
import com.health.dto.RegisterRequest;
import com.health.dto.SendSmsCodeRequest;
import com.health.dto.SmsCodeResponse;
import com.health.service.AuthService;
import com.health.service.CaptchaService;
import com.health.service.SmsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication", description = "User registration and login")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final SmsService smsService;

    public AuthController(AuthService authService,
                          CaptchaService captchaService,
                          SmsService smsService) {
        this.authService = authService;
        this.captchaService = captchaService;
        this.smsService = smsService;
    }

    @Operation(summary = "Create image captcha")
    @GetMapping("/captcha")
    public Result<CaptchaChallengeResponse> createCaptcha() {
        return Result.success(captchaService.createChallenge());
    }

    @Operation(summary = "Send SMS code")
    @PostMapping("/sms-code")
    public Result<SmsCodeResponse> sendSmsCode(@Valid @RequestBody SendSmsCodeRequest request) {
        return Result.success(smsService.sendCode(request));
    }

    @Log("User registration")
    @Operation(summary = "User registration")
    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @Log("User login")
    @Operation(summary = "User login")
    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @Log("User phone code login")
    @Operation(summary = "User login with phone SMS code")
    @PostMapping("/login/phone-code")
    public Result<AuthResponse> loginWithPhoneCode(@Valid @RequestBody PhoneCodeLoginRequest request) {
        return Result.success(authService.loginWithPhoneCode(request));
    }

    @Log("User logout")
    @Operation(summary = "User logout")
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }
}
