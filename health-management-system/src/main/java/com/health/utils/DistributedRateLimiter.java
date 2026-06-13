package com.health.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 分布式速率限制器
 * 基于Redis实现，支持滑动窗口算法
 */
@Slf4j
@Component
public class DistributedRateLimiter {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 固定窗口限流
     * 
     * @param key 限流key
     * @param limit 限流次数
     * @param timeout 时间窗口（秒）
     * @return true-允许访问，false-被限流
     */
    public boolean allowRequest(String key, long limit, long timeout) {
        String countKey = "rate_limit:" + key;
        
        try {
            // 使用INCR命令计数
            Long count = redisTemplate.opsForValue().increment(countKey);
            
            // 第一次访问时设置过期时间
            if (count != null && count == 1) {
                redisTemplate.expire(countKey, timeout, TimeUnit.SECONDS);
            }
            
            // 检查是否超过限制
            if (count != null && count > limit) {
                log.warn("Rate limit exceeded for key: {}, count: {}, limit: {}", key, count, limit);
                return false;
            }
            
            return true;
        } catch (Exception e) {
            log.error("Rate limit check failed for key: {}", key, e);
            // Redis异常时允许访问，避免影响业务
            return true;
        }
    }

    /**
     * 滑动窗口限流（使用Lua脚本保证原子性）
     * 
     * @param key 限流key
     * @param limit 限流次数
     * @param window 时间窗口（秒）
     * @return true-允许访问，false-被限流
     */
    public boolean allowRequestSlidingWindow(String key, long limit, long window) {
        String luaScript = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            
            -- 移除时间窗口外的记录
            redis.call('zremrangebyscore', key, 0, now - window)
            
            -- 获取当前窗口内的请求数
            local count = redis.call('zcard', key)
            
            -- 检查是否超过限制
            if count >= limit then
                return 0
            end
            
            -- 添加当前请求
            redis.call('zadd', key, now, now)
            redis.call('expire', key, window)
            
            return 1
        """;
        
        try {
            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(luaScript, Long.class);
            
            Long result = redisTemplate.execute(
                redisScript,
                Collections.singletonList("rate_limit:sliding:" + key),
                String.valueOf(limit),
                String.valueOf(window),
                String.valueOf(System.currentTimeMillis() / 1000)
            );
            
            return result != null && result == 1;
        } catch (Exception e) {
            log.error("Sliding window rate limit check failed for key: {}", key, e);
            return true;
        }
    }

    /**
     * 令牌桶限流
     * 
     * @param key 限流key
     * @param capacity 桶容量
     * @param rate 令牌生成速率（个/秒）
     * @return true-允许访问，false-被限流
     */
    public boolean allowRequestTokenBucket(String key, long capacity, double rate) {
        String luaScript = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])
            
            -- 获取当前桶的状态
            local info = redis.call('hmget', key, 'tokens', 'last_refill')
            local tokens = tonumber(info[1]) or capacity
            local last_refill = tonumber(info[2]) or now
            
            -- 计算需要补充的令牌数
            local delta = math.max(0, (now - last_refill) * rate)
            tokens = math.min(capacity, tokens + delta)
            
            -- 检查是否有足够的令牌
            if tokens >= requested then
                tokens = tokens - requested
                redis.call('hmset', key, 'tokens', tokens, 'last_refill', now)
                redis.call('expire', key, math.ceil(capacity / rate) + 1)
                return 1
            else
                redis.call('hmset', key, 'tokens', tokens, 'last_refill', now)
                redis.call('expire', key, math.ceil(capacity / rate) + 1)
                return 0
            end
        """;
        
        try {
            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(luaScript, Long.class);
            
            Long result = redisTemplate.execute(
                redisScript,
                Collections.singletonList("rate_limit:token:" + key),
                String.valueOf(capacity),
                String.valueOf(rate),
                String.valueOf(System.currentTimeMillis() / 1000),
                "1"  // 每次请求消耗1个令牌
            );
            
            return result != null && result == 1;
        } catch (Exception e) {
            log.error("Token bucket rate limit check failed for key: {}", key, e);
            return true;
        }
    }

    /**
     * 获取剩余请求次数
     * 
     * @param key 限流key
     * @param limit 限流次数
     * @return 剩余请求次数
     */
    public long getRemainingRequests(String key, long limit) {
        String countKey = "rate_limit:" + key;
        
        try {
            String countStr = redisTemplate.opsForValue().get(countKey);
            if (countStr == null) {
                return limit;
            }
            
            long count = Long.parseLong(countStr);
            return Math.max(0, limit - count);
        } catch (Exception e) {
            log.error("Get remaining requests failed for key: {}", key, e);
            return limit;
        }
    }

    /**
     * 重置限流
     * 
     * @param key 限流key
     */
    public void reset(String key) {
        try {
            redisTemplate.delete("rate_limit:" + key);
            redisTemplate.delete("rate_limit:sliding:" + key);
            redisTemplate.delete("rate_limit:token:" + key);
        } catch (Exception e) {
            log.error("Reset rate limit failed for key: {}", key, e);
        }
    }
}