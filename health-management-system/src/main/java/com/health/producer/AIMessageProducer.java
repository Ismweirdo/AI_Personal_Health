package com.health.producer;

import com.health.message.AIRequestMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * AI消息生产者
 */
@Slf4j
@Component
public class AIMessageProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送AI请求消息
     */
    public void sendAIRequest(AIRequestMessage message) {
        try {
            message.setTimestamp(System.currentTimeMillis());
            rabbitTemplate.convertAndSend("ai.exchange", "ai.request", message);
            log.info("AI request message sent successfully: userId={}, sessionId={}", 
                    message.getUserId(), message.getSessionId());
        } catch (Exception e) {
            log.error("Failed to send AI request message: userId={}, sessionId={}", 
                    message.getUserId(), message.getSessionId(), e);
            throw new RuntimeException("Failed to send AI request", e);
        }
    }

    /**
     * 发送AI响应消息
     */
    public void sendAIResponse(com.health.message.AIResponseMessage message) {
        try {
            message.setTimestamp(System.currentTimeMillis());
            rabbitTemplate.convertAndSend("ai.exchange", "ai.response", message);
            log.info("AI response message sent successfully: userId={}, sessionId={}", 
                    message.getUserId(), message.getSessionId());
        } catch (Exception e) {
            log.error("Failed to send AI response message: userId={}, sessionId={}", 
                    message.getUserId(), message.getSessionId(), e);
        }
    }
}