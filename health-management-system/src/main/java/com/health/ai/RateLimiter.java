package com.health.ai;

import com.health.exception.AIServiceException;
import com.health.exception.AIServiceException.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 速率限制器
 * 用于防止AI服务被滥用
 */
@Slf4j
@Component
public class RateLimiter {
    
    /**
     * 存储每个用户的请求计数
     * key: 用户ID，value: 请求计数和时间戳
     */
    private final Map<Long, RateLimitInfo> userRequestCounts = new ConcurrentHashMap<>();
    
    /**
     * 存储全局请求计数（用于未认证用户）
     */
    private final Map<String, RateLimitInfo> ipRequestCounts = new ConcurrentHashMap<>();
    
    private final AIConfig config;
    
    public RateLimiter(AIConfig config) {
        this.config = config;
    }
    
    /**
     * 检查用户是否超过速率限制
     * @param userId 用户ID（可为null，表示未认证用户）
     * @param ipAddress 用户IP地址
     * @throws AIServiceException 如果超过速率限制
     */
    public void checkRateLimit(Long userId, String ipAddress) throws AIServiceException {
        String key = userId != null ? "user_" + userId : "ip_" + ipAddress;
        RateLimitInfo info = userRequestCounts.computeIfAbsent(userId != null ? userId : 0L, 
                k -> new RateLimitInfo());
        
        synchronized (info) {
            long now = System.currentTimeMillis();
            
            // 如果时间窗口已过，重置计数
            if (now - info.timestamp > 60000) { // 60秒窗口
                info.count = 1;
                info.timestamp = now;
            } else {
                info.count++;
                
                // 检查是否超过限制
                if (info.count > config.getRateLimitPerMinute()) {
                    log.warn("用户 {} 超过速率限制: {} 请求/分钟", key, info.count);
                    throw new AIServiceException(ErrorCode.RATE_LIMITED);
                }
            }
        }
    }
    
    /**
     * 验证输入内容
     * @param message 用户输入消息
     * @throws AIServiceException 如果输入无效
     */
    public void validateInput(String message) throws AIServiceException {
        if (message == null || message.trim().isEmpty()) {
            throw new AIServiceException(ErrorCode.INVALID_INPUT, "输入内容不能为空");
        }
        
        // 检查长度限制
        if (message.length() > config.getMaxInputLength()) {
            throw new AIServiceException(ErrorCode.INVALID_INPUT, 
                    "输入内容过长，最大允许 " + config.getMaxInputLength() + " 个字符");
        }
        
        // 检查是否包含非法内容（简单检查）
        if (containsInvalidContent(message)) {
            throw new AIServiceException(ErrorCode.CONTENT_FILTERED, "输入内容包含非法字符");
        }
    }
    
    /**
     * 检查消息是否包含非法内容
     */
    private boolean containsInvalidContent(String message) {
        // 简单的内容检查，可以根据需要扩展
        String[] invalidPatterns = {"<script>", "</script>", "<iframe>", "javascript:", "vbscript:"};
        String lowerMessage = message.toLowerCase();
        
        for (String pattern : invalidPatterns) {
            if (lowerMessage.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 速率限制信息
     */
    private static class RateLimitInfo {
        int count = 0;
        long timestamp = 0;
    }
}