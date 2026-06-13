package com.health.exception;

/**
 * AI服务异常类
 */
public class AIServiceException extends RuntimeException {
    
    /**
     * 错误码
     */
    private final ErrorCode errorCode;
    
    /**
     * 是否可重试
     */
    private final boolean retryable;
    
    public enum ErrorCode {
        /**
         * API密钥无效
         */
        INVALID_API_KEY("无效的API密钥"),
        
        /**
         * 请求超时
         */
        TIMEOUT("请求超时"),
        
        /**
         * 速率限制
         */
        RATE_LIMITED("请求过于频繁，请稍后重试"),
        
        /**
         * 服务不可用
         */
        SERVICE_UNAVAILABLE("AI服务暂时不可用"),
        
        /**
         * 无效输入
         */
        INVALID_INPUT("输入内容无效"),
        
        /**
         * 内容安全限制
         */
        CONTENT_FILTERED("内容不符合安全规范"),
        
        /**
         * 服务器错误
         */
        SERVER_ERROR("服务器内部错误"),
        
        /**
         * 权限不足
         */
        FORBIDDEN("访问权限不足"),
        
        /**
         * 请求参数错误
         */
        BAD_REQUEST("请求参数错误"),
        
        /**
         * 未知错误
         */
        UNKNOWN("未知错误");
        
        private final String message;
        
        ErrorCode(String message) {
            this.message = message;
        }
        
        public String getMessage() {
            return message;
        }
    }
    
    public AIServiceException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.retryable = isRetryable(errorCode);
    }
    
    public AIServiceException(ErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.errorCode = errorCode;
        this.retryable = isRetryable(errorCode);
    }
    
    public AIServiceException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.retryable = isRetryable(errorCode);
    }
    
    public AIServiceException(ErrorCode errorCode, String detailMessage, Throwable cause) {
        super(detailMessage, cause);
        this.errorCode = errorCode;
        this.retryable = isRetryable(errorCode);
    }
    
    private boolean isRetryable(ErrorCode errorCode) {
        return switch (errorCode) {
            case TIMEOUT, SERVICE_UNAVAILABLE, RATE_LIMITED -> true;
            default -> false;
        };
    }
    
    public ErrorCode getErrorCode() {
        return errorCode;
    }
    
    public boolean isRetryable() {
        return retryable;
    }
}