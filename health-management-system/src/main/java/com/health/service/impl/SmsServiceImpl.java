package com.health.service.impl;

import com.health.common.ErrorCode;
import com.health.dto.SendSmsCodeRequest;
import com.health.dto.SmsCodeResponse;
import com.health.exception.BusinessException;
import com.health.service.CaptchaService;
import com.health.service.SmsService;
import com.health.utils.CacheUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SmsServiceImpl implements SmsService {
    private static final String CODE_PREFIX = "sms:code:";
    private static final String RESEND_PREFIX = "sms:resend:";
    private static final String ALERT_PREFIX = "sms:alert:";
    private static final int CODE_TTL_SECONDS = 300;
    private static final int RESEND_AFTER_SECONDS = 60;
    private static final int ALERT_COOLDOWN_MINUTES = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CacheUtils cacheUtils;
    private final CaptchaService captchaService;
    private final boolean debugMode;

    public SmsServiceImpl(CacheUtils cacheUtils,
                          CaptchaService captchaService,
                          @Value("${sms.debug-mode:true}") boolean debugMode) {
        this.cacheUtils = cacheUtils;
        this.captchaService = captchaService;
        this.debugMode = debugMode;
    }

    @Override
    public SmsCodeResponse sendCode(SendSmsCodeRequest request) {
        String phone = normalizePhone(request.getPhone());
        String purpose = normalizePurpose(request.getPurpose());
        if (!captchaService.verifyAndConsume(request.getCaptchaToken(), request.getSelectedOptionId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "人机验证失败，请重新选择正确图像");
        }

        String resendKey = RESEND_PREFIX + purpose + ":" + phone;
        if (cacheUtils.get(resendKey) != null) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        cacheUtils.set(CODE_PREFIX + purpose + ":" + phone, code, CODE_TTL_SECONDS, TimeUnit.SECONDS);
        cacheUtils.set(resendKey, "1", RESEND_AFTER_SECONDS, TimeUnit.SECONDS);

        sendText(phone, buildCodeMessage(purpose, code));
        return new SmsCodeResponse(
                "验证码已发送",
                CODE_TTL_SECONDS,
                RESEND_AFTER_SECONDS,
                debugMode ? code : null
        );
    }

    @Override
    public void verifyCode(String phone, String purpose, String code) {
        String normalizedPhone = normalizePhone(phone);
        String normalizedPurpose = normalizePurpose(purpose);
        String key = CODE_PREFIX + normalizedPurpose + ":" + normalizedPhone;
        String cached = cacheUtils.get(key);
        if (cached == null || code == null || !cached.equals(code.trim())) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE);
        }
        cacheUtils.delete(key);
    }

    @Override
    public void sendHealthAlert(Long userId, String phone, String content) {
        String normalizedPhone = normalizePhone(phone);
        String key = ALERT_PREFIX + userId + ":" + normalizedPhone + ":" + Integer.toHexString(content.hashCode());
        if (cacheUtils.get(key) != null) {
            log.info("健康异常短信提醒处于冷却期，跳过发送: userId={}, phone={}", userId, maskPhone(normalizedPhone));
            return;
        }
        cacheUtils.set(key, "1", ALERT_COOLDOWN_MINUTES, TimeUnit.MINUTES);
        sendText(normalizedPhone, "【健康管家】" + content);
    }

    private void sendText(String phone, String message) {
        if (debugMode) {
            log.info("[模拟短信] phone={}, content={}", maskPhone(phone), message);
            return;
        }
        log.warn("短信服务未配置真实供应商，已跳过实际发送。phone={}, content={}", maskPhone(phone), message);
    }

    private String buildCodeMessage(String purpose, String code) {
        String scene = "register".equals(purpose) ? "注册" : "登录";
        return "【健康管家】您的" + scene + "验证码为 " + code + "，5分钟内有效。";
    }

    private String normalizePhone(String phone) {
        String value = phone == null ? "" : phone.trim();
        if (!value.matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException(ErrorCode.PHONE_FORMAT_ERROR);
        }
        return value;
    }

    private String normalizePurpose(String purpose) {
        String value = purpose == null ? "" : purpose.trim().toLowerCase();
        if (!"register".equals(value) && !"login".equals(value)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码用途不正确");
        }
        return value;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return "unknown";
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
