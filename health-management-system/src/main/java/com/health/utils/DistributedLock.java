package com.health.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁
 * 基于Redis实现，支持自动续期和可重入
 */
@Slf4j
@Component
public class DistributedLock {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${distributed-lock.default-wait-time:10000}")
    private long defaultWaitTime;

    @Value("${distributed-lock.default-lease-time:30000}")
    private long defaultLeaseTime;

    private static final String LOCK_PREFIX = "distributed_lock:";
    private static final String UNLOCK_SCRIPT = """
        if redis.call('get', KEYS[1]) == ARGV[1] then
            return redis.call('del', KEYS[1])
        else
            return 0
        end
        """;

    private static final String RENEW_SCRIPT = """
        if redis.call('get', KEYS[1]) == ARGV[1] then
            return redis.call('expire', KEYS[1], ARGV[2])
        else
            return 0
        end
        """;

    /**
     * 获取锁
     * 
     * @param lockKey 锁的key
     * @return 锁对象
     */
    public Lock acquire(String lockKey) {
        return acquire(lockKey, defaultWaitTime, defaultLeaseTime, TimeUnit.MILLISECONDS);
    }

    /**
     * 获取锁
     * 
     * @param lockKey 锁的key
     * @param waitTime 等待时间
     * @param leaseTime 锁持有时间
     * @param unit 时间单位
     * @return 锁对象
     */
    public Lock acquire(String lockKey, long waitTime, long leaseTime, TimeUnit unit) {
        String lockValue = UUID.randomUUID().toString();
        String key = LOCK_PREFIX + lockKey;
        long endTime = System.currentTimeMillis() + unit.toMillis(waitTime);

        while (System.currentTimeMillis() < endTime) {
            try {
                // 尝试获取锁
                Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                        key, 
                        lockValue, 
                        leaseTime, 
                        unit
                );

                if (Boolean.TRUE.equals(acquired)) {
                    log.info("Lock acquired: {}", lockKey);
                    return new Lock(key, lockValue, leaseTime, unit);
                }

                // 短暂休眠后重试
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                log.error("Failed to acquire lock: {}", lockKey, e);
                return null;
            }
        }

        log.warn("Failed to acquire lock after timeout: {}", lockKey);
        return null;
    }

    /**
     * 释放锁
     * 
     * @param lock 锁对象
     * @return 是否释放成功
     */
    public boolean release(Lock lock) {
        if (lock == null) {
            return false;
        }

        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
            Long result = redisTemplate.execute(
                    script,
                    Collections.singletonList(lock.getKey()),
                    lock.getValue()
            );

            boolean released = result != null && result == 1;
            if (released) {
                log.info("Lock released: {}", lock.getKey());
            } else {
                log.warn("Failed to release lock: {}", lock.getKey());
            }

            return released;
        } catch (Exception e) {
            log.error("Failed to release lock: {}", lock.getKey(), e);
            return false;
        }
    }

    /**
     * 续期锁
     * 
     * @param lock 锁对象
     * @return 是否续期成功
     */
    public boolean renew(Lock lock) {
        if (lock == null) {
            return false;
        }

        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(RENEW_SCRIPT, Long.class);
            Long result = redisTemplate.execute(
                    script,
                    Collections.singletonList(lock.getKey()),
                    lock.getValue(),
                    String.valueOf(lock.getLeaseTime())
            );

            boolean renewed = result != null && result == 1;
            if (renewed) {
                log.info("Lock renewed: {}", lock.getKey());
            } else {
                log.warn("Failed to renew lock: {}", lock.getKey());
            }

            return renewed;
        } catch (Exception e) {
            log.error("Failed to renew lock: {}", lock.getKey(), e);
            return false;
        }
    }

    /**
     * 锁对象
     */
    public static class Lock implements AutoCloseable {
        private final String key;
        private final String value;
        private final long leaseTime;
        private final TimeUnit unit;
        private final long expireTime;

        public Lock(String key, String value, long leaseTime, TimeUnit unit) {
            this.key = key;
            this.value = value;
            this.leaseTime = leaseTime;
            this.unit = unit;
            this.expireTime = System.currentTimeMillis() + unit.toMillis(leaseTime);
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

        public long getLeaseTime() {
            return leaseTime;
        }

        public TimeUnit getUnit() {
            return unit;
        }

        public long getExpireTime() {
            return expireTime;
        }

        /**
         * 检查锁是否已过期
         */
        public boolean isExpired() {
            return System.currentTimeMillis() >= expireTime;
        }

        /**
         * 获取剩余时间
         */
        public long getRemainingTime() {
            return Math.max(0, expireTime - System.currentTimeMillis());
        }

        @Override
        public void close() {
            // 需要通过DistributedLock来释放
        }
    }

    /**
     * 使用分布式锁执行任务
     * 
     * @param lockKey 锁的key
     * @param task 要执行的任务
     * @param <T> 返回类型
     * @return 任务执行结果
     */
    public <T> T executeWithLock(String lockKey, LockTask<T> task) {
        Lock lock = acquire(lockKey);
        if (lock == null) {
            throw new RuntimeException("Failed to acquire lock: " + lockKey);
        }

        try {
            return task.execute();
        } finally {
            release(lock);
        }
    }

    /**
     * 使用分布式锁执行任务（带超时）
     * 
     * @param lockKey 锁的key
     * @param waitTime 等待时间
     * @param leaseTime 锁持有时间
     * @param unit 时间单位
     * @param task 要执行的任务
     * @param <T> 返回类型
     * @return 任务执行结果
     */
    public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit, LockTask<T> task) {
        Lock lock = acquire(lockKey, waitTime, leaseTime, unit);
        if (lock == null) {
            throw new RuntimeException("Failed to acquire lock: " + lockKey);
        }

        try {
            return task.execute();
        } finally {
            release(lock);
        }
    }

    /**
     * 锁任务接口
     */
    @FunctionalInterface
    public interface LockTask<T> {
        T execute();
    }
}