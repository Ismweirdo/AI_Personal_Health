package com.health.service.impl;

import com.health.dto.CaptchaChallengeResponse;
import com.health.service.CaptchaService;
import com.health.utils.CacheUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class CaptchaServiceImpl implements CaptchaService {
    private static final String CAPTCHA_PREFIX = "captcha:image:";
    private static final int CAPTCHA_TTL_SECONDS = 180;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CacheUtils cacheUtils;

    public CaptchaServiceImpl(CacheUtils cacheUtils) {
        this.cacheUtils = cacheUtils;
    }

    @Override
    public CaptchaChallengeResponse createChallenge() {
        List<ChallengeItem> items = List.of(
                new ChallengeItem("phone", "手机", "#3b6b57", phoneSvg()),
                new ChallengeItem("watch", "手表", "#79684e", watchSvg()),
                new ChallengeItem("leaf", "叶子", "#5f7c68", leafSvg()),
                new ChallengeItem("cup", "水杯", "#a34a3a", cupSvg())
        );
        ChallengeItem answer = items.get(RANDOM.nextInt(items.size()));
        List<ChallengeItem> shuffled = new ArrayList<>(items);
        Collections.shuffle(shuffled, RANDOM);

        String token = UUID.randomUUID().toString();
        cacheUtils.set(CAPTCHA_PREFIX + token, answer.id(), CAPTCHA_TTL_SECONDS, TimeUnit.SECONDS);

        CaptchaChallengeResponse response = new CaptchaChallengeResponse();
        response.setToken(token);
        response.setPrompt("请选择图中的" + answer.label());
        response.setExpiresInSeconds(CAPTCHA_TTL_SECONDS);
        response.setOptions(shuffled.stream()
                .map(item -> new CaptchaChallengeResponse.Option(item.id(), toDataUri(item.svg()), item.label()))
                .toList());
        return response;
    }

    @Override
    public boolean verifyAndConsume(String token, String selectedOptionId) {
        if (token == null || token.isBlank() || selectedOptionId == null || selectedOptionId.isBlank()) {
            return false;
        }
        String key = CAPTCHA_PREFIX + token.trim();
        String answer = cacheUtils.get(key);
        cacheUtils.delete(key);
        return answer != null && answer.equals(selectedOptionId.trim());
    }

    private String toDataUri(String svg) {
        String encoded = java.util.Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        return "data:image/svg+xml;base64," + encoded;
    }

    private String phoneSvg() {
        return """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120">
                  <rect width="120" height="120" rx="8" fill="#fffdf6"/>
                  <rect x="38" y="18" width="44" height="84" rx="8" fill="none" stroke="#3b6b57" stroke-width="8"/>
                  <circle cx="60" cy="90" r="4" fill="#3b6b57"/>
                </svg>
                """;
    }

    private String watchSvg() {
        return """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120">
                  <rect width="120" height="120" rx="8" fill="#fffdf6"/>
                  <path d="M49 15h22l4 22H45zM45 83h30l-4 22H49z" fill="#d8ccb7"/>
                  <rect x="36" y="34" width="48" height="52" rx="15" fill="none" stroke="#79684e" stroke-width="8"/>
                  <path d="M49 60h10l6-12 7 22" fill="none" stroke="#3b6b57" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
                """;
    }

    private String leafSvg() {
        return """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120">
                  <rect width="120" height="120" rx="8" fill="#fffdf6"/>
                  <path d="M88 25C49 27 28 48 30 82c34 2 56-20 58-57z" fill="none" stroke="#5f7c68" stroke-width="8" stroke-linejoin="round"/>
                  <path d="M35 84c17-22 32-34 51-48" fill="none" stroke="#5f7c68" stroke-width="6" stroke-linecap="round"/>
                </svg>
                """;
    }

    private String cupSvg() {
        return """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120">
                  <rect width="120" height="120" rx="8" fill="#fffdf6"/>
                  <path d="M35 36h42l-5 48H40z" fill="none" stroke="#a34a3a" stroke-width="8" stroke-linejoin="round"/>
                  <path d="M77 45h8c9 0 9 22-2 22h-7" fill="none" stroke="#a34a3a" stroke-width="7" stroke-linecap="round"/>
                  <path d="M45 26c0 5 6 5 6 10M61 24c0 6 6 6 6 12" fill="none" stroke="#79684e" stroke-width="5" stroke-linecap="round"/>
                </svg>
                """;
    }

    private record ChallengeItem(String id, String label, String color, String svg) {}
}
