package com.health.controller;

import com.health.ai.AIProvider;
import com.health.annotation.RateLimit;
import com.health.dto.AIActionDraftRequest;
import com.health.dto.AIActionDraftResponse;
import com.health.dto.AIChatSessionResponse;
import com.health.dto.AIRecommendedQuestionResponse;
import com.health.dto.HealthGoalResponse;
import com.health.dto.ReminderRuleResponse;
import com.health.entity.AIChatMessage;
import com.health.message.AIRequestMessage;
import com.health.producer.AIMessageProducer;
import com.health.service.AIAssistantActionService;
import com.health.service.AIService;
import com.health.utils.JwtUtils;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/ai")
public class AIController {

    @Autowired
    private AIService aiService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private AIMessageProducer aiMessageProducer;

    @Autowired
    private AIAssistantActionService aiAssistantActionService;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    // 存储异步请求的SSE发射器
    private final Map<String, SseEmitter> asyncEmitters = new ConcurrentHashMap<>();

    private Long getCurrentUserId() {
        Long userId = jwtUtils.getCurrentUserId();
        return userId != null ? userId : 1L;
    }

    /**
     * 处理AI聊天请求（同步模式，直接返回AI回复）
     */
    @PostMapping("/chat")
    @RateLimit(type = RateLimit.LimitType.USER, limit = 30, timeout = 1, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> request) {
        try {
            Long userId = getCurrentUserId();
            Map<String, Object> response = aiService.handleChatRequest(userId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("AI controller error: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "SERVER_ERROR");
            errorResponse.put("message", "服务器内部错误");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 流式聊天接口，供前端 SSE 使用
     */
    @PostMapping("/chat/stream")
    @RateLimit(type = RateLimit.LimitType.USER, limit = 30, timeout = 1, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public SseEmitter streamChat(@RequestBody Map<String, Object> request) {
        Long userId = getCurrentUserId();
        String sessionId = (String) request.getOrDefault("chatId", UUID.randomUUID().toString());
        Map<String, Object> streamRequest = new HashMap<>(request);
        streamRequest.put("chatId", sessionId);
        return handleStreamChat(userId, sessionId, streamRequest);
    }

    /**
     * 处理流式聊天请求
     */
    private SseEmitter handleStreamChat(Long userId, String sessionId, Map<String, Object> request) {
        SseEmitter emitter = new SseEmitter(120000L); // 2分钟超时
        asyncEmitters.put(sessionId, emitter);

        emitter.onCompletion(() -> asyncEmitters.remove(sessionId));
        emitter.onTimeout(() -> {
            asyncEmitters.remove(sessionId);
            emitter.complete();
        });

        executorService.execute(() -> {
            try {
                aiService.handleStreamChatRequest(userId, request, chunk -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("message")
                                .data(chunk));
                    } catch (IOException e) {
                        log.warn("SSE发送失败: {}", e.getMessage());
                    }
                });

                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data("done"));

                emitter.complete();

            } catch (Exception e) {
                log.error("AI流式聊天错误: {}", e.getMessage(), e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
    
    /**
     * 获取聊天历史
     * @return 聊天消息列表
     */
    @GetMapping("/history")
    public ResponseEntity<List<AIChatMessage>> getChatHistory(@RequestParam(required = false) String chatId) {
        try {
            Long userId = getCurrentUserId();
            List<AIChatMessage> history = aiService.getChatHistory(userId, chatId);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            log.error("AI controller error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<AIChatSessionResponse>> getChatSessions() {
        try {
            Long userId = getCurrentUserId();
            return ResponseEntity.ok(aiService.getChatSessions(userId));
        } catch (Exception e) {
            log.error("AI session list error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/recommended-questions")
    public ResponseEntity<List<AIRecommendedQuestionResponse>> getRecommendedQuestions() {
        try {
            Long userId = getCurrentUserId();
            return ResponseEntity.ok(aiService.getRecommendedQuestions(userId));
        } catch (Exception e) {
            log.error("AI recommended question error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 生成 AI 行动草案，协助创建目标和提醒
     */
    @PostMapping("/assistant/draft")
    @RateLimit(type = RateLimit.LimitType.USER, limit = 20, timeout = 1, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public ResponseEntity<AIActionDraftResponse> generateAssistantDraft(@Valid @RequestBody AIActionDraftRequest request) {
        try {
            Long userId = getCurrentUserId();
            return ResponseEntity.ok(aiAssistantActionService.generateDraft(userId, request.getInstruction()));
        } catch (Exception e) {
            log.error("AI行动草案生成失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 执行 AI 助手指令，直接创建目标和提醒
     * 示例指令：
     * - "帮我创建一个每天走8000步的目标"
     * - "提醒我每天早上8点吃药"
     * - "设置一个每周运动30分钟的目标，并提醒我每周一晚上7点去跑步"
     */
    @PostMapping("/assistant/execute")
    @RateLimit(type = RateLimit.LimitType.USER, limit = 10, timeout = 1, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public ResponseEntity<Map<String, Object>> executeAssistantAction(@Valid @RequestBody AIActionDraftRequest request) {
        try {
            Long userId = getCurrentUserId();
            Map<String, Object> result = aiAssistantActionService.executeAction(userId, request.getInstruction());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("AI助手执行失败: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 从指令直接创建目标
     */
    @PostMapping("/assistant/create-goal")
    @RateLimit(type = RateLimit.LimitType.USER, limit = 10, timeout = 1, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public ResponseEntity<Map<String, Object>> createGoalFromInstruction(@Valid @RequestBody AIActionDraftRequest request) {
        try {
            Long userId = getCurrentUserId();
            Map<String, Object> result = new HashMap<>();
            result.put("instruction", request.getInstruction());
            HealthGoalResponse goal = aiAssistantActionService.createGoalFromDraft(userId, request.getInstruction());
            result.put("success", true);
            result.put("goal", goal);
            result.put("message", "已成功创建" + goal.getTypeLabel() + "目标，目标值：" + goal.getTargetValue() + goal.getUnit());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("从指令创建目标失败: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 从指令直接创建提醒
     */
    @PostMapping("/assistant/create-reminder")
    @RateLimit(type = RateLimit.LimitType.USER, limit = 10, timeout = 1, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public ResponseEntity<Map<String, Object>> createReminderFromInstruction(@Valid @RequestBody AIActionDraftRequest request) {
        try {
            Long userId = getCurrentUserId();
            Map<String, Object> result = new HashMap<>();
            result.put("instruction", request.getInstruction());
            ReminderRuleResponse reminder = aiAssistantActionService.createReminderFromDraft(userId, request.getInstruction());
            result.put("success", true);
            result.put("reminder", reminder);
            result.put("message", "已成功创建" + reminder.getTitle() + "提醒");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("从指令创建提醒失败: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    /**
     * 清空聊天历史
     * @return 响应状态
     */
    @DeleteMapping("/history")
    public ResponseEntity<Void> clearChatHistory(@RequestParam(required = false) String chatId) {
        try {
            Long userId = getCurrentUserId();
            if (chatId != null && !chatId.isBlank()) {
                aiService.deleteChatSession(userId, chatId);
            } else {
                aiService.clearChatHistory(userId);
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("AI controller error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * 获取当前使用的AI服务提供商
     * @return 当前提供商信息
     */
    @GetMapping("/provider/current")
    public ResponseEntity<Map<String, Object>> getCurrentProvider() {
        try {
            AIProvider provider = aiService.getCurrentProvider();
            Map<String, Object> response = new HashMap<>();
            response.put("code", provider.getCode());
            response.put("name", provider.getName());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("AI controller error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * 获取所有可用的AI服务提供商
     * @return 可用提供商列表
     */
    @GetMapping("/provider/available")
    public ResponseEntity<List<Map<String, Object>>> getAvailableProviders() {
        try {
            List<AIProvider> providers = aiService.getAvailableProviders();
            List<Map<String, Object>> result = providers.stream()
                    .map(p -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("code", p.getCode());
                        map.put("name", p.getName());
                        return map;
                    })
                    .collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("AI controller error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * 切换AI服务提供商
     * @param request 包含provider字段的请求体
     * @return 切换结果
     */
    @PostMapping("/provider/switch")
    public ResponseEntity<Map<String, Object>> switchProvider(@RequestBody Map<String, String> request) {
        try {
            String providerCode = request.get("provider");
            if (providerCode == null || providerCode.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "INVALID_PROVIDER");
                error.put("message", "提供商代码不能为空");
                return ResponseEntity.badRequest().body(error);
            }
            
            AIProvider provider = AIProvider.fromCode(providerCode);
            aiService.switchProvider(provider);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("provider", provider.getName());
            response.put("message", "AI服务提供商已切换为: " + provider.getName());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("AI controller error: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "SWITCH_FAILED");
            error.put("message", "切换提供商失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
