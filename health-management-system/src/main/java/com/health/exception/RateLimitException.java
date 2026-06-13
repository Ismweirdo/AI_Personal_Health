package com.health.exception;

/**
 * 速率限制异常
 * 当请求超过速率限制时抛出
 */
public class RateLimitException extends RuntimeException {

    public RateLimitException(String message) {
        super(message);
    }

    public RateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}