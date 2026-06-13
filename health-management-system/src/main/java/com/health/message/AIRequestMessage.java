package com.health.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * AI请求消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIRequestMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 用户消息
     */
    private String message;

    /**
     * 请求时间戳
     */
    private Long timestamp;

    /**
     * 是否流式响应
     */
    private Boolean stream;

    /**
     * AI提供商
     */
    private String provider;
}