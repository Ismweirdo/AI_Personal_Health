package com.health.service;

import com.health.dto.CaptchaChallengeResponse;

public interface CaptchaService {
    CaptchaChallengeResponse createChallenge();

    boolean verifyAndConsume(String token, String selectedOptionId);
}
