package com.health.service;

import com.health.ai.AIProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI服务集成测试
 */
@SpringBootTest
class AIServiceIntegrationTest {
    
    @Autowired
    private AIService aiService;
    
    @Test
    @DisplayName("测试获取当前提供商")
    void testGetCurrentProvider() {
        AIProvider provider = aiService.getCurrentProvider();
        
        assertNotNull(provider);
        assertTrue(provider == AIProvider.MOCK || provider == AIProvider.BAIDU_WENXIN);
    }
    
    @Test
    @DisplayName("测试获取可用提供商列表")
    void testGetAvailableProviders() {
        var providers = aiService.getAvailableProviders();
        
        assertNotNull(providers);
        assertTrue(providers.size() >= 1);
        assertTrue(providers.contains(AIProvider.MOCK));
    }
    
    @Test
    @DisplayName("测试切换到模拟模式")
    void testSwitchProvider_ToMock() {
        aiService.switchProvider(AIProvider.MOCK);
        
        assertEquals(AIProvider.MOCK, aiService.getCurrentProvider());
    }
    
    @Test
    @Transactional
    @DisplayName("测试模拟模式下的聊天功能")
    void testChatWithMockProvider() {
        aiService.switchProvider(AIProvider.MOCK);
        
        Map<String, Object> request = new HashMap<>();
        request.put("message", "你好");
        request.put("chatId", "test-chat-id");
        
        Map<String, Object> response = aiService.handleChatRequest(1L, request);
        
        assertNotNull(response);
        assertFalse(response.containsKey("error"));
        assertTrue(response.containsKey("response"));
        assertTrue(response.containsKey("chatId"));
        assertTrue(response.containsKey("provider"));
        
        assertEquals("test-chat-id", response.get("chatId"));
        assertEquals(AIProvider.MOCK.getName(), response.get("provider"));
    }
    
    @Test
    @Transactional
    @DisplayName("测试处理聊天请求 - 包含上下文")
    void testChatWithContext() {
        aiService.switchProvider(AIProvider.MOCK);
        
        Map<String, Object> request = new HashMap<>();
        request.put("message", "那应该怎么做？");
        request.put("chatId", "test-chat-id-with-context");
        
        Map<String, Object> response = aiService.handleChatRequest(1L, request);
        
        assertNotNull(response);
        assertTrue(response.containsKey("response"));
    }
    
    @Test
    @Transactional
    @DisplayName("测试清空聊天历史")
    void testClearChatHistory() {
        aiService.switchProvider(AIProvider.MOCK);
        Map<String, Object> request = new HashMap<>();
        request.put("message", "测试消息");
        request.put("chatId", "test-clear-history");
        aiService.handleChatRequest(1L, request);
        
        aiService.clearChatHistory(1L);
        
        var history = aiService.getChatHistory(1L);
        assertNotNull(history);
    }
    
    @Test
    @Transactional
    @DisplayName("测试获取聊天历史")
    void testGetChatHistory() {
        aiService.switchProvider(AIProvider.MOCK);
        
        for (int i = 0; i < 3; i++) {
            Map<String, Object> request = new HashMap<>();
            request.put("message", "消息" + i);
            request.put("chatId", "test-history");
            aiService.handleChatRequest(1L, request);
        }
        
        var history = aiService.getChatHistory(1L);
        
        assertNotNull(history);
    }
    
    @Test
    @DisplayName("测试切换到文心一言提供商 - 配置完整")
    void testSwitchProvider_ToWenxin_WithConfig() {
        try {
            aiService.switchProvider(AIProvider.BAIDU_WENXIN);
            assertEquals(AIProvider.BAIDU_WENXIN, aiService.getCurrentProvider());
        } catch (IllegalArgumentException e) {
            assertEquals(AIProvider.MOCK, aiService.getCurrentProvider());
        }
    }
}
