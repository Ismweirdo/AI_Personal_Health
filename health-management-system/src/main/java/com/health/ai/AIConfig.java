package com.health.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI服务配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AIConfig {
    
    /**
     * 默认AI服务提供商
     */
    private String defaultProvider = "mock";
    
    /**
     * 请求超时时间（毫秒）
     */
    private int timeout = 30000;
    
    /**
     * 最大重试次数
     */
    private int maxRetries = 3;
    
    /**
     * 速率限制：每分钟最大请求数
     */
    private int rateLimitPerMinute = 30;
    
    /**
     * 最大输入长度
     */
    private int maxInputLength = 2000;
    
    /**
     * 最大输出长度
     */
    private int maxOutputLength = 4000;
    
    /**
     * OpenAI配置
     */
    private OpenAIConfig openai = new OpenAIConfig();
    
    /**
     * DeepSeek配置
     */
    private DeepSeekConfig deepseek = new DeepSeekConfig();

    /**
     * 通义千问配置
     */
    private QwenConfig qwen = new QwenConfig();

    /**
     * 百度文心一言配置
     */
    private WenxinConfig wenxin = new WenxinConfig();
    
    @Data
    public static class OpenAIConfig {
        /**
         * API密钥
         */
        private String apiKey;
        
        /**
         * API基础URL
         */
        private String baseUrl = "https://api.openai.com/v1";
        
        /**
         * 使用的模型
         */
        private String model = "gpt-3.5-turbo";
        
        /**
         * 温度参数（0-2）
         */
        private double temperature = 0.7;
    }
    
    @Data
    public static class DeepSeekConfig {
        private String apiKey;
        private String baseUrl = "https://api.deepseek.com/v1";
        private String model = "deepseek-chat";
        private double temperature = 0.7;
    }

    @Data
    public static class QwenConfig {
        private String apiKey;
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        private String model = "qwen-plus";
        private double temperature = 0.7;
    }

    @Data
    public static class WenxinConfig {
        /**
         * API密钥
         */
        private String apiKey;
        
        /**
         * 密钥
         */
        private String secretKey;
        
        /**
         * API基础URL
         */
        private String baseUrl = "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat";
        
        /**
         * 使用的模型
         */
        private String model = "completions";
        
        /**
         * 温度参数（0-2）
         */
        private double temperature = 0.7;
    }
}