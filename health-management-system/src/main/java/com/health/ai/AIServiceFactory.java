package com.health.ai;

import com.health.ai.impl.DeepSeekAdapter;
import com.health.ai.impl.MockAIAdapter;
import com.health.ai.impl.OpenAIAdapter;
import com.health.ai.impl.QwenAdapter;
import com.health.ai.impl.WenxinAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI服务工厂
 * 负责管理和提供不同AI服务提供商的适配器
 */
@Slf4j
@Component
public class AIServiceFactory {
    
    private final Map<AIProvider, AIServiceAdapter> adapters = new EnumMap<>(AIProvider.class);
    private final AIConfig config;
    
    public AIServiceFactory(AIConfig config,
                           OpenAIAdapter openAIAdapter,
                           WenxinAdapter wenxinAdapter,
                           DeepSeekAdapter deepSeekAdapter,
                           QwenAdapter qwenAdapter,
                           MockAIAdapter mockAIAdapter) {
        this.config = config;
        this.adapters.put(AIProvider.OPENAI, openAIAdapter);
        this.adapters.put(AIProvider.BAIDU_WENXIN, wenxinAdapter);
        this.adapters.put(AIProvider.DEEPSEEK, deepSeekAdapter);
        this.adapters.put(AIProvider.QWEN, qwenAdapter);
        this.adapters.put(AIProvider.MOCK, mockAIAdapter);
        
        log.info("AI服务工厂初始化完成，已注册 {} 个服务提供商", adapters.size());
    }
    
    /**
     * 获取默认AI服务适配器
     * @return 默认配置的AI服务适配器
     */
    public AIServiceAdapter getDefaultAdapter() {
        AIProvider provider = AIProvider.fromCode(config.getDefaultProvider());
        AIServiceAdapter adapter = adapters.get(provider);
        
        // 如果配置的服务不可用，降级到模拟模式
        if (adapter != null && adapter.isAvailable()) {
            return adapter;
        } else {
            log.warn("配置的AI服务 {} 不可用，降级到模拟模式", provider.getName());
            return adapters.get(AIProvider.MOCK);
        }
    }
    
    /**
     * 根据提供商类型获取适配器
     * @param provider AI服务提供商
     * @return 对应的AI服务适配器
     */
    public AIServiceAdapter getAdapter(AIProvider provider) {
        AIServiceAdapter adapter = adapters.get(provider);
        
        if (adapter != null && adapter.isAvailable()) {
            return adapter;
        } else {
            log.warn("AI服务 {} 不可用，降级到模拟模式", provider.getName());
            return adapters.get(AIProvider.MOCK);
        }
    }
    
    /**
     * 根据提供商代码获取适配器
     * @param providerCode 提供商代码
     * @return 对应的AI服务适配器
     */
    public AIServiceAdapter getAdapter(String providerCode) {
        AIProvider provider = AIProvider.fromCode(providerCode);
        return getAdapter(provider);
    }
    
    /**
     * 获取所有可用的服务提供商列表
     * @return 可用的AI服务提供商列表
     */
    public List<AIProvider> getAvailableProviders() {
        return adapters.entrySet().stream()
                .filter(entry -> entry.getValue().isAvailable())
                .map(Map.Entry::getKey)
                .toList();
    }
    
    /**
     * 检查指定提供商是否可用
     * @param provider AI服务提供商
     * @return true表示可用，false表示不可用
     */
    public boolean isProviderAvailable(AIProvider provider) {
        AIServiceAdapter adapter = adapters.get(provider);
        return adapter != null && adapter.isAvailable();
    }
    
    /**
     * 获取当前配置的默认提供商
     * @return 默认AI服务提供商
     */
    public AIProvider getDefaultProvider() {
        return AIProvider.fromCode(config.getDefaultProvider());
    }
}