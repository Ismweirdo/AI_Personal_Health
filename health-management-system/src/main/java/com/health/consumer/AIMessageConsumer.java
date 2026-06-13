package com.health.consumer;

import com.health.message.AIRequestMessage;
import com.health.message.AIResponseMessage;
import com.health.producer.AIMessageProducer;
import com.health.service.AIService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * AI消息消费者
 * 异步处理AI请求
 */
@Slf4j
@Component
public class AIMessageConsumer {

    @Autowired
    private AIService aiService;

    @Autowired
    private AIMessageProducer aiMessageProducer;

    /**
     * 消费AI请求消息
     */
    @RabbitListener(queues = "ai.request.queue", containerFactory = "rabbitListenerContainerFactory")
    public void consumeAIRequest(AIRequestMessage request) {
        log.info("Received AI request: userId={}, sessionId={}, message={}", 
                request.getUserId(), request.getSessionId(), request.getMessage());

        AIResponseMessage response = AIResponseMessage.builder()
                .userId(request.getUserId())
                .sessionId(request.getSessionId())
                .done(true)
                .build();

        try {
            Map<String, Object> chatRequest = new HashMap<>();
            chatRequest.put("message", request.getMessage());
            chatRequest.put("chatId", request.getSessionId());

            Map<String, Object> aiResult = aiService.handleChatRequest(request.getUserId(), chatRequest);
            String aiResponse = (String) aiResult.getOrDefault("response", "抱歉，暂时未获取到AI响应。");
            
            response.setContent(aiResponse);
            response.setError((String) aiResult.get("message"));
            
            log.info("AI request processed successfully: userId={}, sessionId={}", 
                    request.getUserId(), request.getSessionId());
            
        } catch (Exception e) {
            log.error("Failed to process AI request: userId={}, sessionId={}", 
                    request.getUserId(), request.getSessionId(), e);
            response.setError(e.getMessage());
            response.setContent("抱歉，处理您的请求时出现错误，请稍后重试。");
        }

        // 发送响应消息
        aiMessageProducer.sendAIResponse(response);
    }
}
