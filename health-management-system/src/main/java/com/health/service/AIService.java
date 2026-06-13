package com.health.service;

import com.health.ai.AIProvider;
import com.health.dto.AIChatSessionResponse;
import com.health.dto.AIRecommendedQuestionResponse;
import com.health.entity.AIChatMessage;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * AI服务接口
 */
public interface AIService {
    
    /**
     * 处理AI聊天请求（同步模式）
     * @param userId 用户ID
     * @param request 请求参数，包含message、chatId和context
     * @return AI回复
     */
    Map<String, Object> handleChatRequest(Long userId, Map<String, Object> request);
    
    /**
     * 处理AI聊天请求（流式模式）
     * @param userId 用户ID
     * @param request 请求参数，包含message、chatId和context
     * @param chunkCallback 流式数据回调
     * @return chatId
     */
    String handleStreamChatRequest(Long userId, Map<String, Object> request, Consumer<String> chunkCallback);
    
    /**
     * 获取用户的聊天历史
     * @param userId 用户ID
     * @return 聊天消息列表
     */
    List<AIChatMessage> getChatHistory(Long userId);

    List<AIChatMessage> getChatHistory(Long userId, String chatId);

    List<AIChatSessionResponse> getChatSessions(Long userId);

    List<AIRecommendedQuestionResponse> getRecommendedQuestions(Long userId);

    void deleteChatSession(Long userId, String chatId);
    
    /**
     * 清空用户的聊天历史
     * @param userId 用户ID
     */
    void clearChatHistory(Long userId);
    
    /**
     * 生成AI回复（同步模式）
     * @param message 用户消息
     * @param context 上下文
     * @return AI回复内容
     */
    String generateAIResponse(String message, List<Map<String, Object>> context);
    
    /**
     * 获取当前使用的AI服务提供商
     * @return AI服务提供商
     */
    AIProvider getCurrentProvider();
    
    /**
     * 切换AI服务提供商
     * @param provider 目标提供商
     */
    void switchProvider(AIProvider provider);
    
    /**
     * 获取所有可用的AI服务提供商
     * @return 可用提供商列表
     */
    List<AIProvider> getAvailableProviders();
}
