package com.health.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.ai.AIProvider;
import com.health.ai.AIServiceAdapter;
import com.health.ai.AIServiceFactory;
import com.health.ai.RateLimiter;
import com.health.ai.impl.WenxinAdapter;
import com.health.dto.AIChatSessionResponse;
import com.health.dto.AIRecommendedQuestionResponse;
import com.health.entity.AIChatMessage;
import com.health.entity.DeviceDataLog;
import com.health.entity.HealthData;
import com.health.entity.HealthDevice;
import com.health.entity.HealthGoal;
import com.health.entity.NotificationRecord;
import com.health.entity.ReminderRule;
import com.health.exception.AIServiceException;
import com.health.repository.AIChatMessageRepository;
import com.health.repository.DeviceDataLogRepository;
import com.health.repository.HealthDataRepository;
import com.health.repository.HealthDeviceRepository;
import com.health.repository.HealthGoalRepository;
import com.health.repository.NotificationRecordRepository;
import com.health.repository.ReminderRuleRepository;
import com.health.service.AIService;
import com.health.utils.CacheUtils;
import com.health.utils.HealthMetricSupport;
import com.health.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * AI服务实现类
 * 支持多种AI服务提供商，集成速率限制和输入验证
 */
@Slf4j
@Service
public class AIServiceImpl implements AIService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<String> COMMON_METRIC_ORDER = Arrays.asList(
            "steps", "heart_rate", "sleep", "weight", "blood_pressure", "blood_sugar", "exercise", "diet", "mood"
    );
    private static final Map<String, String> METRIC_LABELS = Map.ofEntries(
            Map.entry("steps", "步数"),
            Map.entry("heart_rate", "心率"),
            Map.entry("sleep", "睡眠时长"),
            Map.entry("weight", "体重"),
            Map.entry("blood_pressure", "血压"),
            Map.entry("blood_sugar", "血糖"),
            Map.entry("exercise", "运动"),
            Map.entry("diet", "饮食"),
            Map.entry("mood", "情绪")
    );
    private static final Map<String, String> METRIC_UNITS = Map.ofEntries(
            Map.entry("steps", "步"),
            Map.entry("heart_rate", "bpm"),
            Map.entry("sleep", "小时"),
            Map.entry("weight", "kg"),
            Map.entry("blood_pressure", "mmHg"),
            Map.entry("blood_sugar", "mmol/L"),
            Map.entry("exercise", "分钟")
    );

    private static final String AI_CACHE_PREFIX = "ai:chat:";
    private static final int AI_CACHE_TTL_MINUTES = 30;
    private static final int MAX_CONTEXT_MESSAGES = 4;
    private static final int MAX_CONTEXT_MESSAGE_LENGTH = 500;
    private static final int MAX_ENRICHED_CONTEXT_LENGTH = 3000;
    private static final int MAX_USER_INPUT_LENGTH = 1200;
    private static final int MAX_RECOMMENDED_QUESTIONS = 6;

    private static final Map<String, String> SHORT_METRIC_LABELS = Map.ofEntries(
            Map.entry("steps", "步数"),
            Map.entry("heart_rate", "心率"),
            Map.entry("sleep", "睡眠"),
            Map.entry("weight", "体重"),
            Map.entry("blood_pressure", "血压"),
            Map.entry("blood_sugar", "血糖"),
            Map.entry("exercise", "运动"),
            Map.entry("diet", "饮食"),
            Map.entry("mood", "情绪")
    );

    private static final ConcurrentHashMap<String, Long> PROMPT_TOKEN_COUNTER = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> COMPLETION_TOKEN_COUNTER = new ConcurrentHashMap<>();
    private static final AtomicLong TOTAL_PROMPT_TOKENS = new AtomicLong(0);
    private static final AtomicLong TOTAL_COMPLETION_TOKENS = new AtomicLong(0);

    @Autowired
    private AIChatMessageRepository aiChatMessageRepository;

    @Autowired
    private AIServiceFactory aiServiceFactory;

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private HealthDataRepository healthDataRepository;

    @Autowired
    private HealthGoalRepository healthGoalRepository;

    @Autowired
    private ReminderRuleRepository reminderRuleRepository;

    @Autowired
    private NotificationRecordRepository notificationRecordRepository;

    @Autowired
    private HealthDeviceRepository healthDeviceRepository;

    @Autowired
    private DeviceDataLogRepository deviceDataLogRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private CacheUtils cacheUtils;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 当前使用的AI服务提供商（支持运行时切换）
     */
    private final AtomicReference<AIProvider> currentProvider = new AtomicReference<>();

    @Override
    public Map<String, Object> handleChatRequest(Long userId, Map<String, Object> request) {
        Long currentUserId = resolveCurrentUserId(userId);
        String message = preprocessInput((String) request.get("message"));
        String chatId = normalizeChatId((String) request.get("chatId"));
        List<Map<String, Object>> context = resolveConversationContext(currentUserId, chatId, castContext(request.get("context")));
        AIProvider provider = getCurrentProvider();
        String providerName = provider.getName();

        String cacheKey = buildAICacheKey(currentUserId, providerName, message, context);

        try {
            String cachedResponse = cacheUtils.get(cacheKey);
            if (cachedResponse != null && !cachedResponse.isEmpty()) {
                log.info("[缓存命中] 用户={}, 提供商={}, 问题={}", currentUserId, providerName, truncateForLog(message));
                saveChatMessage(currentUserId, chatId, "user", message);
                saveChatMessage(currentUserId, chatId, "assistant", cachedResponse);

                Map<String, Object> response = new HashMap<>();
                response.put("response", cachedResponse);
                response.put("chatId", chatId);
                response.put("provider", providerName);
                response.put("contextEnabled", true);
                response.put("cacheHit", true);
                return response;
            }
        } catch (Exception e) {
            log.debug("读取AI缓存失败: {}", e.getMessage());
        }

        try {
            rateLimiter.validateInput(message);
            rateLimiter.checkRateLimit(currentUserId, getClientIp());

            saveChatMessage(currentUserId, chatId, "user", message);

            String enrichedMessage = enrichWithUserContext(currentUserId, message);
            long promptTokens = estimateTokens(enrichedMessage) + estimateContextTokens(context);
            TOTAL_PROMPT_TOKENS.addAndGet(promptTokens);
            PROMPT_TOKEN_COUNTER.merge(providerName, promptTokens, Long::sum);

            String aiResponse = generateAIResponseWithRetry(enrichedMessage, context);
            aiResponse = sanitizeAssistantResponse(aiResponse);

            long completionTokens = estimateTokens(aiResponse);
            TOTAL_COMPLETION_TOKENS.addAndGet(completionTokens);
            COMPLETION_TOKEN_COUNTER.merge(providerName, completionTokens, Long::sum);

            log.info("[Token统计] 提供商={}, prompt_tokens={}, completion_tokens={}, total={}",
                    providerName, promptTokens, completionTokens, promptTokens + completionTokens);

            saveChatMessage(currentUserId, chatId, "assistant", aiResponse);

            try {
                cacheUtils.set(cacheKey, aiResponse, AI_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
                log.debug("[缓存写入] 用户={}, 提供商={}, 响应长度={}", currentUserId, providerName, aiResponse.length());
            } catch (Exception e) {
                log.debug("写入AI缓存失败: {}", e.getMessage());
            }

            Map<String, Object> response = new HashMap<>();
            response.put("response", aiResponse);
            response.put("chatId", chatId);
            response.put("provider", providerName);
            response.put("contextEnabled", true);
            response.put("cacheHit", false);
            response.put("promptTokens", promptTokens);
            response.put("completionTokens", completionTokens);
            return response;

        } catch (AIServiceException e) {
            log.error("AI服务处理失败: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getErrorCode().name());
            errorResponse.put("message", e.getMessage());
            errorResponse.put("retryable", e.isRetryable());
            return errorResponse;
        } catch (Exception e) {
            log.error("AI服务处理异常: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "UNKNOWN");
            errorResponse.put("message", "服务器内部错误，请稍后重试");
            errorResponse.put("retryable", true);
            return errorResponse;
        }
    }

    /**
     * 生成AI回复（带重试机制）
     */
    private String generateAIResponseWithRetry(String message, List<Map<String, Object>> context) {
        AIProvider current = getCurrentProvider();
        AIServiceAdapter adapter = aiServiceFactory.getAdapter(current);
        int maxRetries = 3;
        int attempts = 0;

        while (attempts < maxRetries) {
            try {
                String contextJson = null;
                if (context != null && !context.isEmpty()) {
                    try {
                        contextJson = objectMapper.writeValueAsString(context);
                    } catch (Exception e) {
                        log.warn("序列化上下文失败: {}", e.getMessage());
                    }
                }

                log.info("正在调用{}处理请求，消息长度: {} 字符", current.getName(), message.length());
                String response = adapter.generateResponse(message, contextJson);
                log.info("{}调用成功，响应长度: {} 字符", current.getName(), response.length());
                return response;

            } catch (AIServiceException e) {
                attempts++;
                log.warn("AI服务调用失败（第{}次尝试），提供商: {}, 错误: {}", attempts, current.getName(), e.getMessage());

                if (!e.isRetryable() || attempts >= maxRetries) {
                    if (e.getErrorCode() == AIServiceException.ErrorCode.INVALID_API_KEY) {
                        log.error("{} API Key无效或已过期，请检查配置", current.getName());
                        throw new AIServiceException(
                                AIServiceException.ErrorCode.INVALID_API_KEY,
                                current.getName() + " API Key无效或已过期，请检查配置文件中的API Key"
                        );
                    }

                    log.warn("降级到模拟模式");
                    adapter = aiServiceFactory.getAdapter(AIProvider.MOCK);
                    try {
                        String mockResponse = adapter.generateResponse(message, null);
                return "当前" + current.getName() + "服务暂时不可用，已自动切换到模拟模式。\n\n" + sanitizeAssistantResponse(mockResponse);
                    } catch (Exception ex) {
                        throw new AIServiceException(AIServiceException.ErrorCode.SERVER_ERROR, ex);
                    }
                }

                try {
                    Thread.sleep((long) Math.pow(2, attempts) * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new AIServiceException(AIServiceException.ErrorCode.SERVER_ERROR, ie);
                }
            }
        }

        throw new AIServiceException(AIServiceException.ErrorCode.SERVER_ERROR);
    }

    private String enrichWithUserContext(Long userId, String originalMessage) {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime metricsStart = now.minusDays(30);
            LocalDateTime deviceLogStart = now.minusDays(7);

            List<HealthData> healthData = healthDataRepository.findByUserIdAndRecordDateBetweenOrderByRecordDateAsc(
                    userId, metricsStart, now
            );
            List<HealthGoal> goals = healthGoalRepository.findByUserIdAndEnabledTrueOrderByCreatedAtDesc(userId);
            List<ReminderRule> reminders = reminderRuleRepository.findByUserIdOrderByCreatedAtDesc(userId);
            List<NotificationRecord> notifications = notificationRecordRepository.findByUserIdOrderByCreatedAtDesc(userId);
            List<HealthDevice> devices = healthDeviceRepository.findByUserId(userId);
            List<DeviceDataLog> deviceLogs = deviceDataLogRepository.findByUserIdAndCreatedAtBetween(userId, deviceLogStart, now);

            StringBuilder sb = new StringBuilder();
            sb.append("助手任务说明：\n");
            sb.append("你是健康管家里的 AI 助手。请基于下面的用户健康数据回答。\n");
            sb.append("回答要亲和、直接、容易执行。不要使用 Markdown 标题符号，不要输出 #、---、***、表格或代码块。\n");
            sb.append("请优先使用短段落和简洁编号，例如“1. 先看结论”。每次回答尽量包含：结论、依据、建议、需要就医的情况。\n");
            sb.append("如果用户想设置目标或提醒，请告诉用户可以直接说“帮我创建每天8000步目标”或“提醒我每天晚上9点记录体重”，系统会生成草案让用户确认。\n\n");

            appendHealthMetricsContext(sb, healthData);
            appendGoalContext(sb, userId, goals);
            appendReminderContext(sb, reminders);
            appendNotificationContext(sb, notifications);
            appendDeviceContext(sb, devices, deviceLogs);

            if (sb.length() > MAX_ENRICHED_CONTEXT_LENGTH) {
                log.warn("上下文过长({}字符)，截断到{}字符", sb.length(), MAX_ENRICHED_CONTEXT_LENGTH);
                sb.setLength(MAX_ENRICHED_CONTEXT_LENGTH);
                sb.append("...(已截断)");
            }

            sb.append("\n\n[问题]\n").append(originalMessage);

            String result = sb.toString();
            log.debug("上下文注入完成，总长度: {} 字符", result.length());
            return result;
        } catch (Exception e) {
            log.warn("注入用户实时数据失败，使用原始消息: {}", e.getMessage());
            return originalMessage;
        }
    }

    private void appendHealthMetricsContext(StringBuilder sb, List<HealthData> healthData) {
        sb.append("健康指标: ");
        if (healthData == null || healthData.isEmpty()) {
            sb.append("无\n");
            return;
        }

        Map<String, List<HealthData>> grouped = healthData.stream()
                .collect(Collectors.groupingBy(HealthData::getType, LinkedHashMap::new, Collectors.toList()));

        List<String> orderedTypes = resolveMetricOrder(grouped);
        for (String type : orderedTypes) {
            List<HealthData> items = grouped.get(type);
            if (items == null || items.isEmpty()) continue;
            DoubleSummaryStatistics stats = items.stream().mapToDouble(HealthData::getDataValue).summaryStatistics();
            HealthData latest = items.get(items.size() - 1);
            String unit = resolveMetricUnit(type, latest.getUnit());
            sb.append(SHORT_METRIC_LABELS.getOrDefault(type, type)).append("=")
                    .append(formatDouble(latest.getDataValue())).append(unit)
                    .append("/均值").append(formatDouble(stats.getAverage())).append(unit)
                    .append("/共").append(items.size()).append("条; ");
        }
        sb.append("\n");
    }

    private void appendGoalContext(StringBuilder sb, Long userId, List<HealthGoal> goals) {
        sb.append("健康目标: ");
        if (goals == null || goals.isEmpty()) {
            sb.append("无\n");
            return;
        }
        for (HealthGoal goal : goals.stream().limit(5).collect(Collectors.toList())) {
            GoalSnapshot snapshot = buildGoalSnapshot(userId, goal);
            sb.append(SHORT_METRIC_LABELS.getOrDefault(goal.getType(), goal.getType()))
                    .append("目标").append(formatDouble(goal.getTargetValue())).append(safeUnit(goal.getUnit()))
                    .append("/当前").append(formatDouble(snapshot.currentValue())).append(safeUnit(goal.getUnit()))
                    .append("(完成").append(formatDouble(snapshot.progress())).append("%); ");
        }
        sb.append("\n");
    }

    private void appendReminderContext(StringBuilder sb, List<ReminderRule> reminders) {
        sb.append("提醒: ");
        if (reminders == null || reminders.isEmpty()) {
            sb.append("无\n");
            return;
        }
        long enabledCount = reminders.stream().filter(rule -> Boolean.TRUE.equals(rule.getEnabled())).count();
        sb.append("共").append(reminders.size()).append("条(").append(enabledCount).append("启用); ");
        reminders.stream()
                .filter(rule -> Boolean.TRUE.equals(rule.getEnabled()))
                .limit(3)
                .forEach(rule -> sb.append(rule.getTitle())
                        .append("(").append(getFrequencyLabel(rule.getFrequency())).append(")"));
        sb.append("\n");
    }

    private void appendNotificationContext(StringBuilder sb, List<NotificationRecord> notifications) {
        sb.append("通知: ");
        if (notifications == null || notifications.isEmpty()) {
            sb.append("无\n");
            return;
        }
        long unreadCount = notifications.stream().filter(n -> "unread".equalsIgnoreCase(n.getStatus())).count();
        sb.append("共").append(notifications.size()).append("条(").append(unreadCount).append("未读)\n");
    }

    private void appendDeviceContext(StringBuilder sb, List<HealthDevice> devices, List<DeviceDataLog> deviceLogs) {
        sb.append("设备: ");
        if ((devices == null || devices.isEmpty()) && (deviceLogs == null || deviceLogs.isEmpty())) {
            sb.append("无\n");
            return;
        }
        if (devices != null && !devices.isEmpty()) {
            long activeCount = devices.stream()
                    .filter(d -> "active".equalsIgnoreCase(d.getStatus()) || "online".equalsIgnoreCase(d.getStatus()))
                    .count();
            sb.append("绑定").append(devices.size()).append("台(").append(activeCount).append("在线); ");
        }
        if (deviceLogs != null && !deviceLogs.isEmpty()) {
            long successCount = deviceLogs.stream().filter(log -> "success".equalsIgnoreCase(log.getStatus())).count();
            sb.append("近7天同步").append(deviceLogs.size()).append("次(").append(successCount).append("成功)");
        }
        sb.append("\n");
    }

    private GoalSnapshot buildGoalSnapshot(Long userId, HealthGoal goal) {
        DateRange range = resolveGoalDateRange(goal.getPeriod());
        List<HealthData> records = healthDataRepository.findByUserIdAndTypeAndRecordDateBetween(
                userId,
                goal.getType(),
                range.start(),
                range.end()
        );
        double currentValue = calculateGoalCurrentValue(goal.getType(), records);
        double targetValue = goal.getTargetValue() == null ? 0D : goal.getTargetValue();
        double progress = targetValue <= 0 ? 0D : Math.min(100D, currentValue / targetValue * 100D);
        double remainingValue = Math.max(0D, targetValue - currentValue);
        return new GoalSnapshot(round(currentValue), round(progress), round(remainingValue));
    }

    private double calculateGoalCurrentValue(String type, List<HealthData> records) {
        if (records == null || records.isEmpty()) {
            return 0D;
        }

        Map<LocalDate, List<HealthData>> groupedByDay = records.stream()
                .collect(Collectors.groupingBy(
                        record -> record.getRecordDate().toLocalDate(),
                        TreeMap::new,
                        Collectors.toList()
                ));

        List<Double> dailyValues = groupedByDay.values().stream()
                .map(items -> aggregateDayValue(type, items))
                .collect(Collectors.toList());

        if (HealthMetricSupport.isCumulative(type)) {
            return dailyValues.stream().mapToDouble(Double::doubleValue).sum();
        }
        return dailyValues.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
    }

    private double aggregateDayValue(String type, List<HealthData> items) {
        if (HealthMetricSupport.isCumulative(type)) {
            return items.stream().mapToDouble(HealthData::getDataValue).sum();
        }
        return items.stream().mapToDouble(HealthData::getDataValue).average().orElse(0D);
    }

    private DateRange resolveGoalDateRange(String period) {
        LocalDate today = LocalDate.now();
        LocalDate startDate;
        LocalDate endDate;
        if ("weekly".equalsIgnoreCase(period)) {
            startDate = today.with(DayOfWeek.MONDAY);
            endDate = today.with(DayOfWeek.SUNDAY);
        } else if ("monthly".equalsIgnoreCase(period)) {
            startDate = today.withDayOfMonth(1);
            endDate = today.withDayOfMonth(today.lengthOfMonth());
        } else {
            startDate = today;
            endDate = today;
        }
        return new DateRange(startDate.atStartOfDay(), endDate.atTime(LocalTime.MAX.withNano(0)));
    }

    private List<String> resolveMetricOrder(Map<String, List<HealthData>> grouped) {
        List<String> ordered = new ArrayList<>();
        for (String type : COMMON_METRIC_ORDER) {
            if (grouped.containsKey(type)) {
                ordered.add(type);
            }
        }
        grouped.keySet().stream()
                .filter(type -> !ordered.contains(type))
                .sorted()
                .forEach(ordered::add);
        return ordered;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castContext(Object contextObject) {
        if (contextObject instanceof List<?>) {
            return (List<Map<String, Object>>) contextObject;
        }
        return null;
    }

    private List<Map<String, Object>> resolveConversationContext(Long userId, String chatId, List<Map<String, Object>> requestContext) {
        if (requestContext != null && !requestContext.isEmpty()) {
            return compressContext(limitContext(requestContext));
        }
        if (chatId == null || chatId.isBlank()) {
            return null;
        }

        List<AIChatMessage> storedMessages = aiChatMessageRepository.findByUserIdAndChatIdOrderByTimestampAsc(userId, chatId);
        if (storedMessages == null || storedMessages.isEmpty()) {
            return null;
        }

        int fromIndex = Math.max(0, storedMessages.size() - MAX_CONTEXT_MESSAGES);
        return storedMessages.subList(fromIndex, storedMessages.size()).stream()
                .map(message -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("role", message.getRole());
                    item.put("content", truncateContent(message.getContent(), MAX_CONTEXT_MESSAGE_LENGTH));
                    return item;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> limitContext(List<Map<String, Object>> context) {
        int fromIndex = Math.max(0, context.size() - MAX_CONTEXT_MESSAGES);
        return new ArrayList<>(context.subList(fromIndex, context.size()));
    }

    private List<Map<String, Object>> compressContext(List<Map<String, Object>> context) {
        if (context == null || context.isEmpty()) return context;
        return context.stream()
                .map(item -> {
                    Map<String, Object> compressed = new HashMap<>();
                    compressed.put("role", item.get("role"));
                    Object content = item.get("content");
                    if (content instanceof String str) {
                        compressed.put("content", truncateContent(str, MAX_CONTEXT_MESSAGE_LENGTH));
                    } else {
                        compressed.put("content", content);
                    }
                    return compressed;
                })
                .collect(Collectors.toList());
    }

    private String normalizeChatId(String chatId) {
        return (chatId == null || chatId.isBlank()) ? UUID.randomUUID().toString() : chatId;
    }

    private void saveChatMessage(Long userId, String chatId, String role, String content) {
        AIChatMessage message = new AIChatMessage();
        message.setUserId(userId);
        message.setChatId(chatId);
        message.setRole(role);
        message.setContent(content);
        message.setTimestamp(LocalDateTime.now());
        aiChatMessageRepository.save(message);
    }

    private Long resolveCurrentUserId(Long fallbackUserId) {
        Long jwtUserId = jwtUtils.getCurrentUserId();
        if (jwtUserId != null) {
            return jwtUserId;
        }
        if (fallbackUserId != null) {
            log.debug("JWT上下文不可用，回退到服务端已解析的userId: {}", fallbackUserId);
            return fallbackUserId;
        }
        return 1L;
    }

    private String getMetricLabel(String type) {
        return METRIC_LABELS.getOrDefault(type, type);
    }

    private String resolveMetricUnit(String type, String unit) {
        if (unit != null && !unit.isBlank() && !"?".equals(unit)) {
            return unit;
        }
        return METRIC_UNITS.getOrDefault(type, "");
    }

    private String safeUnit(String unit) {
        return unit == null || unit.isBlank() || "?".equals(unit) ? "" : unit;
    }

    private String safeText(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String formatDateTime(LocalDateTime time) {
        return time == null ? "无" : time.format(DATE_TIME_FORMATTER);
    }

    private String formatDouble(double value) {
        return String.format("%.1f", value);
    }

    private String getPeriodLabel(String period) {
        if ("daily".equalsIgnoreCase(period)) {
            return "每日";
        }
        if ("weekly".equalsIgnoreCase(period)) {
            return "每周";
        }
        if ("monthly".equalsIgnoreCase(period)) {
            return "每月";
        }
        return safeText(period, "未设置");
    }

    private String getFrequencyLabel(String frequency) {
        if ("daily".equalsIgnoreCase(frequency)) {
            return "每日";
        }
        if ("weekly".equalsIgnoreCase(frequency)) {
            return "每周";
        }
        if ("once".equalsIgnoreCase(frequency)) {
            return "单次";
        }
        return safeText(frequency, "未设置");
    }

    private AIChatSessionResponse buildSessionResponse(String chatId, List<AIChatMessage> messages) {
        List<AIChatMessage> ordered = messages.stream()
                .sorted(Comparator.comparing(AIChatMessage::getTimestamp))
                .collect(Collectors.toList());
        AIChatMessage firstUserMessage = ordered.stream()
                .filter(message -> "user".equalsIgnoreCase(message.getRole()))
                .findFirst()
                .orElse(ordered.get(0));
        AIChatMessage latest = ordered.get(ordered.size() - 1);

        AIChatSessionResponse response = new AIChatSessionResponse();
        response.setChatId(chatId);
        response.setTitle(buildSessionTitle(firstUserMessage.getContent()));
        response.setPreview(truncateContent(sanitizePlainText(latest.getContent()), 48));
        response.setUpdatedAt(formatDateTime(latest.getTimestamp()));
        response.setMessageCount(ordered.size());
        return response;
    }

    private String buildSessionTitle(String content) {
        String text = sanitizePlainText(content);
        if (!hasText(text)) {
            return "新的健康对话";
        }
        return truncateContent(text, 18);
    }

    private void addMetricRecommendedQuestions(List<HealthData> records, List<AIRecommendedQuestionResponse> questions) {
        Map<String, List<HealthData>> grouped = records.stream()
                .collect(Collectors.groupingBy(HealthData::getType, LinkedHashMap::new, Collectors.toList()));

        for (String type : resolveMetricOrder(grouped)) {
            List<HealthData> items = grouped.get(type);
            if (items == null || items.isEmpty()) {
                continue;
            }
            HealthData latest = items.get(items.size() - 1);
            double[] range = HealthMetricSupport.getNormalRange(type);
            String label = SHORT_METRIC_LABELS.getOrDefault(type, type);
            String unit = resolveMetricUnit(type, latest.getUnit());
            if (range != null && (latest.getDataValue() < range[0] || latest.getDataValue() > range[1])) {
                questions.add(new AIRecommendedQuestionResponse(
                        "我的" + label + "最近为什么异常？应该怎么处理？",
                        "异常解读",
                        label + "最新值 " + formatDouble(latest.getDataValue()) + unit + " 超出常见范围"
                ));
            } else if (items.size() >= 5) {
                questions.add(new AIRecommendedQuestionResponse(
                        "请分析我最近30天的" + label + "趋势，并给出建议",
                        "趋势分析",
                        "已有 " + items.size() + " 条" + label + "记录"
                ));
            }
            if (questions.size() >= MAX_RECOMMENDED_QUESTIONS) {
                return;
            }
        }
    }

    private void addGoalRecommendedQuestions(Long userId, List<HealthGoal> goals, List<AIRecommendedQuestionResponse> questions) {
        if (goals == null) {
            return;
        }
        for (HealthGoal goal : goals) {
            GoalSnapshot snapshot = buildGoalSnapshot(userId, goal);
            if (snapshot.progress() < 80D) {
                String label = SHORT_METRIC_LABELS.getOrDefault(goal.getType(), goal.getType());
                questions.add(new AIRecommendedQuestionResponse(
                        "我的" + label + "目标进度偏慢，接下来怎么调整？",
                        "目标规划",
                        "当前完成度约 " + formatDouble(snapshot.progress()) + "%"
                ));
            }
            if (questions.size() >= MAX_RECOMMENDED_QUESTIONS) {
                return;
            }
        }
    }

    private void addReminderRecommendedQuestions(List<ReminderRule> reminders, List<AIRecommendedQuestionResponse> questions) {
        if (reminders == null || reminders.isEmpty()) {
            questions.add(new AIRecommendedQuestionResponse(
                    "帮我设置一个每天晚上9点记录健康数据的提醒",
                    "提醒设置",
                    "当前还没有提醒规则"
            ));
            return;
        }
        long enabledCount = reminders.stream().filter(rule -> Boolean.TRUE.equals(rule.getEnabled())).count();
        if (enabledCount == 0) {
            questions.add(new AIRecommendedQuestionResponse(
                    "我现在的提醒都停用了，帮我重新安排关键提醒",
                    "提醒设置",
                    "现有提醒均未启用"
            ));
        }
    }

    private List<AIRecommendedQuestionResponse> defaultRecommendedQuestions() {
        return List.of(
                new AIRecommendedQuestionResponse("请分析我最近的健康数据，有哪些需要注意？", "健康分析", "适合先建立整体认识"),
                new AIRecommendedQuestionResponse("帮我创建一个每天8000步的目标", "目标规划", "可以直接生成目标草案"),
                new AIRecommendedQuestionResponse("提醒我每天晚上9点记录体重", "提醒设置", "可以直接生成提醒草案"),
                new AIRecommendedQuestionResponse("如何改善睡眠质量？", "健康建议", "常见健康管理问题"),
                new AIRecommendedQuestionResponse("适合我的运动计划是什么？", "运动建议", "适合开始制定计划"),
                new AIRecommendedQuestionResponse("心率异常时我应该怎么做？", "异常解读", "了解异常处理思路")
        );
    }

    private String sanitizeAssistantResponse(String response) {
        if (response == null) {
            return "";
        }
        return Arrays.stream(response.split("\\R"))
                .map(this::sanitizeAssistantLine)
                .collect(Collectors.joining("\n"))
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String sanitizeAssistantLine(String line) {
        if (line == null) {
            return "";
        }
        String sanitized = line.trim()
                .replaceAll("^#{1,6}\\s*", "")
                .replaceAll("^[-*_]{3,}$", "")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "");
        sanitized = sanitized.replaceAll("^[-*]\\s+", "• ");
        return sanitized;
    }

    private String sanitizePlainText(String value) {
        return sanitizeAssistantResponse(value).replaceAll("\\s+", " ").trim();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String ip = req.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = req.getHeader("X-Real-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = req.getRemoteAddr();
                }
                return ip != null ? ip : "127.0.0.1";
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }

    @Override
    public String handleStreamChatRequest(Long userId, Map<String, Object> request, Consumer<String> chunkCallback) {
        Long currentUserId = resolveCurrentUserId(userId);
        String message = preprocessInput((String) request.get("message"));
        String chatId = normalizeChatId((String) request.get("chatId"));
        List<Map<String, Object>> context = resolveConversationContext(currentUserId, chatId, castContext(request.get("context")));
        AIProvider provider = getCurrentProvider();
        String providerName = provider.getName();

        String cacheKey = buildAICacheKey(currentUserId, providerName, message, context);

        try {
            String cachedResponse = cacheUtils.get(cacheKey);
            if (cachedResponse != null && !cachedResponse.isEmpty()) {
                log.info("[流式缓存命中] 用户={}, 提供商={}", currentUserId, providerName);
                saveChatMessage(currentUserId, chatId, "user", message);
                chunkCallback.accept(cachedResponse);
                saveChatMessage(currentUserId, chatId, "assistant", cachedResponse);
                return chatId;
            }
        } catch (Exception e) {
            log.debug("读取AI流式缓存失败: {}", e.getMessage());
        }

        try {
            rateLimiter.validateInput(message);
            rateLimiter.checkRateLimit(currentUserId, getClientIp());

            saveChatMessage(currentUserId, chatId, "user", message);

            String enrichedMessage = enrichWithUserContext(currentUserId, message);
            long promptTokens = estimateTokens(enrichedMessage) + estimateContextTokens(context);
            TOTAL_PROMPT_TOKENS.addAndGet(promptTokens);
            PROMPT_TOKEN_COUNTER.merge(providerName, promptTokens, Long::sum);

            AIServiceAdapter adapter = aiServiceFactory.getAdapter(provider);
            String contextJson = null;
            if (context != null && !context.isEmpty()) {
                try {
                    contextJson = objectMapper.writeValueAsString(context);
                } catch (Exception e) {
                    log.warn("序列化上下文失败: {}", e.getMessage());
                }
            }

            StringBuilder fullResponse = new StringBuilder();
            adapter.generateStreamResponse(enrichedMessage, contextJson, chunk -> {
                fullResponse.append(chunk);
                chunkCallback.accept(chunk);
            });

            String finalResponse = fullResponse.toString();
            finalResponse = sanitizeAssistantResponse(finalResponse);
            long completionTokens = estimateTokens(finalResponse);
            TOTAL_COMPLETION_TOKENS.addAndGet(completionTokens);
            COMPLETION_TOKEN_COUNTER.merge(providerName, completionTokens, Long::sum);

            log.info("[流式Token统计] 提供商={}, prompt_tokens={}, completion_tokens={}, total={}",
                    providerName, promptTokens, completionTokens, promptTokens + completionTokens);

            saveChatMessage(currentUserId, chatId, "assistant", finalResponse);

            try {
                cacheUtils.set(cacheKey, finalResponse, AI_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.debug("写入AI流式缓存失败: {}", e.getMessage());
            }

            return chatId;

        } catch (AIServiceException e) {
            log.error("AI服务流式处理失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("AI服务流式处理异常: {}", e.getMessage(), e);
            throw new AIServiceException(AIServiceException.ErrorCode.UNKNOWN, e);
        }
    }

    @Override
    public List<AIChatMessage> getChatHistory(Long userId) {
        return aiChatMessageRepository.findByUserIdOrderByTimestampDesc(userId);
    }

    @Override
    public List<AIChatMessage> getChatHistory(Long userId, String chatId) {
        if (!hasText(chatId)) {
            return getChatHistory(userId);
        }
        return aiChatMessageRepository.findByUserIdAndChatIdOrderByTimestampAsc(userId, chatId.trim());
    }

    @Override
    public List<AIChatSessionResponse> getChatSessions(Long userId) {
        List<AIChatMessage> history = aiChatMessageRepository.findByUserIdOrderByTimestampDesc(userId);
        Map<String, List<AIChatMessage>> grouped = history.stream()
                .collect(Collectors.groupingBy(AIChatMessage::getChatId, LinkedHashMap::new, Collectors.toList()));

        return grouped.entrySet().stream()
                .map(entry -> buildSessionResponse(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(AIChatSessionResponse::getUpdatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<AIRecommendedQuestionResponse> getRecommendedQuestions(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<HealthData> records = healthDataRepository.findByUserIdAndRecordDateBetweenOrderByRecordDateAsc(
                userId, now.minusDays(30), now
        );
        List<HealthGoal> goals = healthGoalRepository.findByUserIdAndEnabledTrueOrderByCreatedAtDesc(userId);
        List<ReminderRule> reminders = reminderRuleRepository.findByUserIdOrderByCreatedAtDesc(userId);

        List<AIRecommendedQuestionResponse> questions = new ArrayList<>();
        if (records != null && records.size() >= 3) {
            addMetricRecommendedQuestions(records, questions);
            addGoalRecommendedQuestions(userId, goals, questions);
            addReminderRecommendedQuestions(reminders, questions);
        }

        if (questions.size() < 3) {
            questions.addAll(defaultRecommendedQuestions());
        }
        return questions.stream()
                .limit(MAX_RECOMMENDED_QUESTIONS)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteChatSession(Long userId, String chatId) {
        if (hasText(chatId)) {
            aiChatMessageRepository.deleteByUserIdAndChatId(userId, chatId.trim());
        }
    }

    @Override
    @Transactional
    public void clearChatHistory(Long userId) {
        aiChatMessageRepository.deleteByUserId(userId);
    }

    @Override
    public String generateAIResponse(String message, List<Map<String, Object>> context) {
        try {
            AIServiceAdapter adapter = aiServiceFactory.getAdapter(getCurrentProvider());
            String contextJson = null;
            if (context != null && !context.isEmpty()) {
                contextJson = objectMapper.writeValueAsString(context);
            }
            return adapter.generateResponse(message, contextJson);
        } catch (AIServiceException e) {
            log.error("AI回复生成失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("AI回复生成异常: {}", e.getMessage(), e);
            throw new AIServiceException(AIServiceException.ErrorCode.UNKNOWN, e);
        }
    }

    @Override
    public AIProvider getCurrentProvider() {
        AIProvider provider = currentProvider.get();
        if (provider == null) {
            provider = aiServiceFactory.getDefaultProvider();
            currentProvider.set(provider);
        }

        if (!aiServiceFactory.isProviderAvailable(provider)) {
            log.warn("配置的提供商 {} 不可用，降级到MOCK模式", provider.getName());
            return AIProvider.MOCK;
        }

        return provider;
    }

    @Override
    public void switchProvider(AIProvider provider) {
        AIServiceAdapter adapter = aiServiceFactory.getAdapter(provider);

        if (!adapter.isAvailable()) {
            log.warn("AI服务提供商 {} 不可用，保持当前提供商", provider.getName());
            throw new IllegalArgumentException("AI服务提供商 " + provider.getName() + " 不可用");
        }

        if (provider == AIProvider.BAIDU_WENXIN) {
            WenxinAdapter wenxinAdapter = (WenxinAdapter) adapter;
            if (!wenxinAdapter.hasCompleteConfig()) {
                log.warn("文心一言API Key未配置");
                throw new IllegalArgumentException("文心一言API Key未配置，请在配置文件中设置WENXIN_API_KEY环境变量");
            }
        }

        currentProvider.set(provider);
        log.info("AI服务提供商已切换为: {}", provider.getName());
    }

    @Override
    public List<AIProvider> getAvailableProviders() {
        return aiServiceFactory.getAvailableProviders();
    }

    private record GoalSnapshot(double currentValue, double progress, double remainingValue) {
    }

    private record DateRange(LocalDateTime start, LocalDateTime end) {
    }

    private String preprocessInput(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String result = input
                .replaceAll("\\s+", " ")
                .trim();
        result = result.replaceAll("https?://\\S+", "[链接]");
        result = result.replaceAll("```[\\s\\S]*?```", "[代码已省略]");
        if (result.length() > MAX_USER_INPUT_LENGTH) {
            log.info("用户输入过长({}字符)，截断到{}字符", result.length(), MAX_USER_INPUT_LENGTH);
            result = result.substring(0, MAX_USER_INPUT_LENGTH) + "...(已截断)";
        }
        return result;
    }

    private String buildAICacheKey(Long userId, String provider, String message, List<Map<String, Object>> context) {
        StringBuilder sb = new StringBuilder();
        sb.append(AI_CACHE_PREFIX).append(userId).append(":").append(provider).append(":");
        StringBuilder rawBuilder = new StringBuilder(message);
        rawBuilder.append("|");
        if (context != null && !context.isEmpty()) {
            for (Map<String, Object> item : context) {
                Object role = item.get("role");
                Object content = item.get("content");
                if (role != null) rawBuilder.append(role);
                if (content != null) {
                    String c = content.toString();
                    rawBuilder.append(c.length() > 50 ? c.substring(0, 50) : c);
                }
            }
        }
        String raw = rawBuilder.toString();
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            sb.append(hex);
        } catch (Exception e) {
            sb.append(Integer.toHexString(raw.hashCode()));
        }
        return sb.toString();
    }

    private long estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        int charCount = text.length();
        boolean hasChinese = text.chars().anyMatch(c -> c >= 0x4E00 && c <= 0x9FFF);
        if (hasChinese) {
            int chineseCount = (int) text.chars().filter(c -> c >= 0x4E00 && c <= 0x9FFF).count();
            int englishCount = charCount - chineseCount;
            return (chineseCount * 2 + englishCount) / 4;
        }
        return charCount / 4;
    }

    private long estimateContextTokens(List<Map<String, Object>> context) {
        if (context == null || context.isEmpty()) return 0;
        long total = 0;
        for (Map<String, Object> item : context) {
            Object content = item.get("content");
            if (content instanceof String str) {
                total += estimateTokens(str);
            }
        }
        return total;
    }

    private String truncateContent(String content, int maxLength) {
        if (content == null) return "";
        if (content.length() <= maxLength) return content;
        return content.substring(0, maxLength) + "...";
    }

    private String truncateForLog(String text) {
        if (text == null || text.length() <= 50) return text;
        return text.substring(0, 50) + "...";
    }

    public static Map<String, Long> getTotalTokenStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("totalPromptTokens", TOTAL_PROMPT_TOKENS.get());
        stats.put("totalCompletionTokens", TOTAL_COMPLETION_TOKENS.get());
        stats.put("totalTokens", TOTAL_PROMPT_TOKENS.get() + TOTAL_COMPLETION_TOKENS.get());
        return stats;
    }

    public static Map<String, Map<String, Long>> getProviderTokenStats() {
        Map<String, Map<String, Long>> stats = new HashMap<>();
        for (String provider : PROMPT_TOKEN_COUNTER.keySet()) {
            Map<String, Long> providerStats = new HashMap<>();
            providerStats.put("promptTokens", PROMPT_TOKEN_COUNTER.getOrDefault(provider, 0L));
            providerStats.put("completionTokens", COMPLETION_TOKEN_COUNTER.getOrDefault(provider, 0L));
            providerStats.put("totalTokens",
                    providerStats.get("promptTokens") + providerStats.get("completionTokens"));
            stats.put(provider, providerStats);
        }
        return stats;
    }
}
