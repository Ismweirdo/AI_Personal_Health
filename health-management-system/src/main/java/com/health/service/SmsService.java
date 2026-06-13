package com.health.service;

import com.health.dto.SendSmsCodeRequest;
import com.health.dto.SmsCodeResponse;

public interface SmsService {
    SmsCodeResponse sendCode(SendSmsCodeRequest request);

    void verifyCode(String phone, String purpose, String code);

    void sendHealthAlert(Long userId, String phone, String content);
}
