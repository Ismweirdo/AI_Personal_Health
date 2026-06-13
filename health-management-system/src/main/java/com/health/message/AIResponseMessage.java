package com.health.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * AI响应消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIResponseMessage implements Serializable {

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
     * AI响应内容
     */
    private String content;

    /**
     * 是否完成
     */
    private Boolean done;

    /**
     * 错误信息（如果有）
     */
    private String error;

    /**
     * 响应时间戳
     */
    private Long timestamp;
}