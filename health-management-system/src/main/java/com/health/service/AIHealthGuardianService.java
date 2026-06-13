package com.health.service;

import com.health.dto.AIHealthGuardianResponse;

public interface AIHealthGuardianService {

    AIHealthGuardianResponse getInsights(Long userId, boolean forceRefresh);
}
