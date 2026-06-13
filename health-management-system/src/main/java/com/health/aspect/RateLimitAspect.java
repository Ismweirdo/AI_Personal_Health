package com.health.aspect;

import com.health.utils.DistributedRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 速率限制切面
 * 使用分布式速率限制器对接口进行限流
 */
@Slf4j
@Aspect
@Component
@Order(1)
public class RateLimitAspect {

    @Autowired
    private DistributedRateLimiter rateLimiter;

    /**
     * 环绕通知，对标记了@RateLimit注解的方法进行限流
     */
    @Around("@annotation(com.health.annotation.RateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        // 获取注解
        com.health.annotation.RateLimit rateLimit = method.getAnnotation(com.health.annotation.RateLimit.class);
        
        if (rateLimit == null) {
            return joinPoint.proceed();
        }
        
        // 构建限流key
        String key = buildRateLimitKey(rateLimit);
        
        // 获取限流参数
        long limit = rateLimit.limit();
        long timeout = rateLimit.timeout();
        TimeUnit timeUnit = rateLimit.timeUnit();
        long timeoutSeconds = timeUnit.toSeconds(timeout);
        
        // 检查限流
        boolean allowed;
        try {
            if (rateLimit.algorithm() == com.health.annotation.RateLimit.Algorithm.SLIDING_WINDOW) {
                allowed = rateLimiter.allowRequestSlidingWindow(key, limit, timeoutSeconds);
            } else if (rateLimit.algorithm() == com.health.annotation.RateLimit.Algorithm.TOKEN_BUCKET) {
                allowed = rateLimiter.allowRequestTokenBucket(key, limit, (double) limit / timeoutSeconds);
            } else {
                allowed = rateLimiter.allowRequest(key, limit, timeoutSeconds);
            }
        } catch (Exception e) {
            log.warn("Rate limiter unavailable, fallback to allow request for key: {}, reason: {}", key, e.getMessage());
            allowed = true;
        }
        
        if (!allowed) {
            log.warn("Rate limit exceeded for key: {}, limit: {}, timeout: {}s", key, limit, timeoutSeconds);
            throw new com.health.exception.RateLimitException("操作过于频繁，请稍后重试");
        }
        
        // 执行方法
        return joinPoint.proceed();
    }

    /**
     * 构建限流key
     */
    private String buildRateLimitKey(com.health.annotation.RateLimit rateLimit) {
        StringBuilder keyBuilder = new StringBuilder();
        
        // 添加限流类型前缀
        keyBuilder.append(rateLimit.type().name().toLowerCase()).append(":");
        
        // 根据类型添加标识
        switch (rateLimit.type()) {
            case USER:
                // 用户ID
                Long userId = getCurrentUserId();
                keyBuilder.append(userId != null ? userId : "anonymous");
                break;
            case IP:
                // IP地址
                String ip = getClientIp();
                keyBuilder.append(ip != null ? ip : "unknown");
                break;
            case DEVICE:
                // 设备ID
                String deviceId = getDeviceId();
                keyBuilder.append(deviceId != null ? deviceId : "unknown");
                break;
            case GLOBAL:
                // 全局限流
                keyBuilder.append("global");
                break;
            default:
                keyBuilder.append("default");
        }
        
        // 添加方法名作为后缀
        keyBuilder.append(":").append(getCurrentMethodName());
        
        return keyBuilder.toString();
    }

    /**
     * 获取当前用户ID
     */
    private Long getCurrentUserId() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                Object userId = request.getAttribute("userId");
                return userId != null ? Long.parseLong(userId.toString()) : null;
            }
        } catch (Exception e) {
            log.error("Get current user id failed", e);
        }
        return null;
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("X-Real-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                // 处理多个IP的情况，取第一个
                if (ip != null && ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        } catch (Exception e) {
            log.error("Get client ip failed", e);
        }
        return null;
    }

    /**
     * 获取设备ID
     */
    private String getDeviceId() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getHeader("X-Device-ID");
            }
        } catch (Exception e) {
            log.error("Get device id failed", e);
        }
        return null;
    }

    /**
     * 获取当前方法名
     */
    private String getCurrentMethodName() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String uri = request.getRequestURI();
                // 提取方法名
                String[] parts = uri.split("/");
                return parts.length > 0 ? parts[parts.length - 1] : "unknown";
            }
        } catch (Exception e) {
            log.error("Get current method name failed", e);
        }
        return "unknown";
    }
}
