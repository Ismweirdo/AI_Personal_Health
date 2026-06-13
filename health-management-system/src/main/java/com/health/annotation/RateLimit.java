package com.health.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 速率限制注解
 * 用于标记需要进行速率限制的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 限流类型
     */
    LimitType type() default LimitType.USER;

    /**
     * 限流次数
     */
    long limit() default 30;

    /**
     * 时间窗口
     */
    long timeout() default 1;

    /**
     * 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.MINUTES;

    /**
     * 限流算法
     */
    Algorithm algorithm() default Algorithm.FIXED_WINDOW;

    /**
     * 限流类型枚举
     */
    enum LimitType {
        /**
         * 用户级别限流
         */
        USER,
        /**
         * IP级别限流
         */
        IP,
        /**
         * 设备级别限流
         */
        DEVICE,
        /**
         * 全局限流
         */
        GLOBAL
    }

    /**
     * 限流算法枚举
     */
    enum Algorithm {
        /**
         * 固定窗口算法
         */
        FIXED_WINDOW,
        /**
         * 滑动窗口算法
         */
        SLIDING_WINDOW,
        /**
         * 令牌桶算法
         */
        TOKEN_BUCKET
    }
}