package com.health.ai;

/**
 * AI服务提供商枚举
 */
public enum AIProvider {
    OPENAI("openai", "OpenAI"),
    BAIDU_WENXIN("baidu_wenxin", "百度文心一言"),
    DEEPSEEK("deepseek", "DeepSeek"),
    QWEN("qwen", "通义千问"),
    MOCK("mock", "模拟模式");
    
    private final String code;
    private final String name;
    
    AIProvider(String code, String name) {
        this.code = code;
        this.name = name;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getName() {
        return name;
    }
    
    public static AIProvider fromCode(String code) {
        for (AIProvider provider : values()) {
            if (provider.code.equalsIgnoreCase(code)) {
                return provider;
            }
        }
        return MOCK;
    }
}