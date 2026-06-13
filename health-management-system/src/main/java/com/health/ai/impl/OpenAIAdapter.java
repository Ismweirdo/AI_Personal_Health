package com.health.ai.impl;

import com.health.ai.AIConfig;
import com.health.ai.AIProvider;
import com.health.ai.HealthAssistantPrompts;
import org.springframework.stereotype.Component;

@Component
public class OpenAIAdapter extends AbstractOpenAICompatibleAdapter {

    private final AIConfig.OpenAIConfig config;

    public OpenAIAdapter(AIConfig aiConfig) {
        this.config = aiConfig.getOpenai();
    }

    @Override public AIProvider getProvider() { return AIProvider.OPENAI; }
    @Override protected String getApiKey() { return config.getApiKey(); }
    @Override protected String getBaseUrl() { return config.getBaseUrl(); }
    @Override protected String getModel() { return config.getModel(); }
    @Override protected double getTemperature() { return config.getTemperature(); }
    @Override
    protected String getSystemPrompt() {
        return HealthAssistantPrompts.SYSTEM_PROMPT;
    }
}
