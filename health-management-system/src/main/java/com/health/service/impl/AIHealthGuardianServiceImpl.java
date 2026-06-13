package com.health.service.impl;

import com.health.dto.AIHealthGuardianResponse;
import com.health.dto.AIHealthInsightItem;
import com.health.entity.DeviceDataLog;
import com.health.entity.HealthData;
import com.health.entity.HealthGoal;
import com.health.entity.HealthDevice;
import com.health.entity.NotificationRecord;
import com.health.entity.ReminderRule;
import com.health.entity.User;
import com.health.repository.DeviceDataLogRepository;
import com.health.repository.HealthDataRepository;
import com.health.repository.HealthDeviceRepository;
import com.health.repository.HealthGoalRepository;
import com.health.repository.NotificationRecordRepository;
import com.health.repository.ReminderRuleRepository;
import com.health.repository.UserRepository;
import com.health.service.AIHealthGuardianService;
import com.health.service.AIService;
import com.health.service.SmsService;
import com.health.utils.DistributedLock;
import com.health.utils.HealthMetricSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AIHealthGuardianServiceImpl implements AIHealthGuardianService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final int MAX_INSIGHTS = 6;
    private static final Map<String, String> LABELS = Map.of(
            "steps", "步数",
            "heart_rate", "心率",
            "sleep", "睡眠",
            "weight", "体重",
            "blood_pressure", "血压",
            "blood_sugar", "血糖",
            "exercise", "运动",
            "diet", "饮食",
            "mood", "情绪"
    );

    @Autowired
    private HealthDataRepository healthDataRepository;

    @Autowired
    private HealthGoalRepository healthGoalRepository;

    @Autowired
    private ReminderRuleRepository reminderRuleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRecordRepository notificationRecordRepository;

    @Autowired
    private HealthDeviceRepository healthDeviceRepository;

    @Autowired
    private DeviceDataLogRepository deviceDataLogRepository;

    @Autowired
    private AIService aiService;

    @Autowired
    private SmsService smsService;

    @Value("${health-alert.sms-enabled:false}")
    private boolean smsAlertEnabled;

    @Autowired
    private DistributedLock distributedLock;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public AIHealthGuardianResponse getInsights(Long userId, boolean forceRefresh) {
        String cacheKey = buildCacheKey(userId);
        if (!forceRefresh) {
            AIHealthGuardianResponse cached = readCache(cacheKey);
            if (cached != null) {
                cached.setCacheHit(true);
                cached.setSourceType("cache");
                return cached;
            }
        }

        DistributedLock.Lock lock = null;
        try {
            lock = distributedLock.acquire(buildLockKey(userId), 1500, 10000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("获取AI主动预警分布式锁失败: {}", e.getMessage());
        }

        if (lock == null) {
            AIHealthGuardianResponse cached = readCache(cacheKey);
            if (cached != null) {
                cached.setCacheHit(true);
                cached.setSourceType("cache");
                return cached;
            }
            AIHealthGuardianResponse direct = buildInsights(userId);
            direct.setSourceType("live");
            direct.setCacheHit(false);
            return direct;
        }

        try {
            if (!forceRefresh) {
                AIHealthGuardianResponse cached = readCache(cacheKey);
                if (cached != null) {
                    cached.setCacheHit(true);
                    cached.setSourceType("cache");
                    return cached;
                }
            }

            AIHealthGuardianResponse response = buildInsights(userId);
            response.setSourceType("live");
            response.setCacheHit(false);
            writeCache(cacheKey, response);
            return response;
        } finally {
            distributedLock.release(lock);
        }
    }

    private AIHealthGuardianResponse buildInsights(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime metricsStart = now.minusDays(7);
        LocalDateTime deviceLogStart = now.minusDays(7);

        List<HealthData> metricRecords = healthDataRepository.findByUserIdAndRecordDateBetweenOrderByRecordDateAsc(
                userId, metricsStart, now
        );
        List<HealthGoal> goals = healthGoalRepository.findByUserIdAndEnabledTrueOrderByCreatedAtDesc(userId);
        List<ReminderRule> reminders = reminderRuleRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<NotificationRecord> notifications = notificationRecordRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<HealthDevice> devices = healthDeviceRepository.findByUserId(userId);
        List<DeviceDataLog> deviceLogs = deviceDataLogRepository.findByUserIdAndCreatedAtBetween(userId, deviceLogStart, now);

        List<AIHealthInsightItem> items = new ArrayList<>();
        items.addAll(buildMetricInsights(metricRecords));
        items.addAll(buildGoalInsights(userId, goals));
        items.addAll(buildReminderInsights(reminders, notifications, now));
        items.addAll(buildDeviceInsights(devices, deviceLogs, now));

        List<AIHealthInsightItem> sortedItems = items.stream()
                .sorted(Comparator.comparingInt(this::severityRank).reversed())
                .limit(MAX_INSIGHTS)
                .collect(Collectors.toList());
        sendInsightSmsAlerts(userId, sortedItems);

        AIHealthGuardianResponse response = new AIHealthGuardianResponse();
        response.setGeneratedAt(now.format(DATE_TIME_FORMATTER));
        response.setItems(sortedItems);
        response.setOverallStatus(resolveOverallStatus(sortedItems));
        response.setSummary(buildSummary(sortedItems));
        return response;
    }

    private void sendInsightSmsAlerts(Long userId, List<AIHealthInsightItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        if (!smsAlertEnabled) {
            return;
        }
        userRepository.findById(userId)
                .map(User::getPhone)
                .filter(phone -> phone != null && !phone.isBlank())
                .ifPresent(phone -> items.stream()
                        .filter(item -> "metric".equals(item.getType()))
                        .filter(item -> "high".equals(item.getSeverity()) || "medium".equals(item.getSeverity()))
                        .forEach(item -> {
                            try {
                                smsService.sendHealthAlert(userId, phone, item.getTitle() + "：" + item.getSummary());
                            } catch (Exception e) {
                                log.warn("AI主动预警短信发送失败: userId={}, insight={}, error={}",
                                        userId, item.getId(), e.getMessage());
                            }
                        }));
    }

    private List<AIHealthInsightItem> buildMetricInsights(List<HealthData> records) {
        List<AIHealthInsightItem> items = new ArrayList<>();
        if (records == null || records.isEmpty()) {
            return items;
        }

        Map<String, List<HealthData>> grouped = records.stream()
                .collect(Collectors.groupingBy(HealthData::getType, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<HealthData>> entry : grouped.entrySet()) {
            String type = entry.getKey();
            double[] range = HealthMetricSupport.getNormalRange(type);
            if (range == null) {
                continue;
            }
            List<HealthData> values = entry.getValue();
            HealthData latest = values.get(values.size() - 1);
            double latestValue = latest.getDataValue();

            if (latestValue < range[0] || latestValue > range[1]) {
                AIHealthInsightItem item = new AIHealthInsightItem();
                item.setId("metric-" + latest.getId());
                item.setType("metric");
                item.setSeverity(isFarOutsideRange(latestValue, range) ? "high" : "medium");
                item.setTitle(getMetricLabel(type) + (latestValue > range[1] ? "偏高预警" : "偏低预警"));
                item.setSummary("最近一次" + getMetricLabel(type) + "为 " + formatDouble(latestValue) + safeUnit(latest.getUnit())
                        + "，已超出正常范围 " + formatDouble(range[0]) + "-" + formatDouble(range[1]) + safeUnit(latest.getUnit()) + "。");
                item.setActionSuggestion("建议尽快复测并结合最近作息、饮食和运动情况排查原因，必要时及时就医。");
                item.setRelatedMetric(type);
                item.setValue(round(latestValue));
                item.setUnit(latest.getUnit());
                items.add(item);
            }
        }

        Map<String, List<HealthData>> groupedByType = records.stream()
                .collect(Collectors.groupingBy(HealthData::getType));
        for (Map.Entry<String, List<HealthData>> entry : groupedByType.entrySet()) {
            String type = entry.getKey();
            List<HealthData> values = entry.getValue().stream()
                    .sorted(Comparator.comparing(HealthData::getRecordDate))
                    .collect(Collectors.toList());
            if (values.size() < 2) {
                continue;
            }
            HealthData first = values.get(0);
            HealthData latest = values.get(values.size() - 1);
            double firstValue = first.getDataValue();
            double latestValue = latest.getDataValue();
            if (firstValue <= 0) {
                continue;
            }
            double changeRate = (latestValue - firstValue) / firstValue;
            if (Math.abs(changeRate) >= 0.35 && !"steps".equals(type)) {
                AIHealthInsightItem item = new AIHealthInsightItem();
                item.setId("trend-" + type);
                item.setType("metric");
                item.setSeverity(Math.abs(changeRate) >= 0.5 ? "high" : "medium");
                item.setTitle(getMetricLabel(type) + "波动明显");
                item.setSummary("近7天" + getMetricLabel(type) + (changeRate > 0 ? "上升" : "下降")
                        + "了约 " + formatDouble(Math.abs(changeRate) * 100) + "%，存在明显波动。");
                item.setActionSuggestion("建议回顾近几天的饮食、休息、运动和用药变化，确认是否存在连续异常。");
                item.setRelatedMetric(type);
                item.setValue(round(latestValue));
                item.setUnit(latest.getUnit());
                items.add(item);
            }
        }

        return items;
    }

    private List<AIHealthInsightItem> buildGoalInsights(Long userId, List<HealthGoal> goals) {
        List<AIHealthInsightItem> items = new ArrayList<>();
        for (HealthGoal goal : goals) {
            GoalSnapshot snapshot = buildGoalSnapshot(userId, goal);
            if (snapshot.progress() < 50D) {
                AIHealthInsightItem item = new AIHealthInsightItem();
                item.setId("goal-" + goal.getId());
                item.setType("goal");
                item.setSeverity(snapshot.progress() < 25D ? "high" : "medium");
                item.setTitle(getMetricLabel(goal.getType()) + "目标进度偏慢");
                item.setSummary(getPeriodLabel(goal.getPeriod()) + "目标当前完成度为 " + formatDouble(snapshot.progress())
                        + "%，距离目标还差 " + formatDouble(snapshot.remainingValue()) + safeUnit(goal.getUnit()) + "。");
                item.setActionSuggestion("建议适当增加记录频率，并将目标拆分为更小的每日行动步骤，便于持续达成。");
                item.setRelatedMetric(goal.getType());
                item.setValue(round(snapshot.progress()));
                item.setUnit("%");
                items.add(item);
            }
        }
        return items;
    }

    private List<AIHealthInsightItem> buildReminderInsights(List<ReminderRule> reminders,
                                                            List<NotificationRecord> notifications,
                                                            LocalDateTime now) {
        List<AIHealthInsightItem> items = new ArrayList<>();

        long disabledRules = reminders.stream().filter(rule -> !Boolean.TRUE.equals(rule.getEnabled())).count();
        if (!reminders.isEmpty() && disabledRules == reminders.size()) {
            AIHealthInsightItem item = new AIHealthInsightItem();
            item.setId("reminder-disabled");
            item.setType("reminder");
            item.setSeverity("medium");
            item.setTitle("提醒规则全部停用");
            item.setSummary("当前已配置提醒规则 " + reminders.size() + " 条，但都处于停用状态。");
            item.setActionSuggestion("建议重新启用关键提醒，避免目标执行和健康管理中断。");
            items.add(item);
        }

        long dueSoon = reminders.stream()
                .filter(rule -> Boolean.TRUE.equals(rule.getEnabled()) && rule.getNextTriggerAt() != null)
                .filter(rule -> !rule.getNextTriggerAt().isBefore(now) && !rule.getNextTriggerAt().isAfter(now.plusHours(24)))
                .count();
        if (dueSoon >= 3) {
            AIHealthInsightItem item = new AIHealthInsightItem();
            item.setId("reminder-due-soon");
            item.setType("reminder");
            item.setSeverity("low");
            item.setTitle("近期提醒较密集");
            item.setSummary("未来24小时内有 " + dueSoon + " 条提醒即将触发，建议提前安排执行节奏。");
            item.setActionSuggestion("可以检查提醒时间是否过于集中，必要时错峰调整。");
            items.add(item);
        }

        long unread = notifications.stream()
                .filter(notification -> "unread".equalsIgnoreCase(notification.getStatus()))
                .count();
        if (unread >= 3) {
            AIHealthInsightItem item = new AIHealthInsightItem();
            item.setId("notification-unread");
            item.setType("notification");
            item.setSeverity(unread >= 6 ? "medium" : "low");
            item.setTitle("未读通知较多");
            item.setSummary("当前有 " + unread + " 条未读通知，可能存在提醒未及时处理。");
            item.setActionSuggestion("建议尽快查看通知中心，避免错过关键提醒与异常提示。");
            items.add(item);
        }
        return items;
    }

    private List<AIHealthInsightItem> buildDeviceInsights(List<HealthDevice> devices,
                                                          List<DeviceDataLog> deviceLogs,
                                                          LocalDateTime now) {
        List<AIHealthInsightItem> items = new ArrayList<>();
        long recentFailures = deviceLogs.stream()
                .filter(log -> "failure".equalsIgnoreCase(log.getStatus()))
                .count();
        if (recentFailures > 0) {
            AIHealthInsightItem item = new AIHealthInsightItem();
            item.setId("device-failure");
            item.setType("device");
            item.setSeverity(recentFailures >= 3 ? "high" : "medium");
            item.setTitle("设备同步存在失败记录");
            item.setSummary("最近7天设备同步失败 " + recentFailures + " 次，可能影响健康数据完整性。");
            item.setActionSuggestion("建议检查设备连接状态、API 密钥和网络环境，避免关键指标漏同步。");
            items.add(item);
        }

        long staleDevices = devices.stream()
                .filter(device -> device.getLastActive() == null || device.getLastActive().isBefore(now.minusDays(2)))
                .count();
        if (staleDevices > 0) {
            AIHealthInsightItem item = new AIHealthInsightItem();
            item.setId("device-stale");
            item.setType("device");
            item.setSeverity("medium");
            item.setTitle("设备活跃度不足");
            item.setSummary("有 " + staleDevices + " 台设备最近 48 小时未活跃，可能导致数据更新延迟。");
            item.setActionSuggestion("建议确认设备佩戴、蓝牙连接与同步状态，保证连续数据采集。");
            items.add(item);
        }

        if (devices.isEmpty()) {
            AIHealthInsightItem item = new AIHealthInsightItem();
            item.setId("device-empty");
            item.setType("device");
            item.setSeverity("low");
            item.setTitle("尚未接入健康设备");
            item.setSummary("当前未绑定任何设备，健康数据主要依赖手动录入。");
            item.setActionSuggestion("如果有可穿戴设备，建议尽快接入以获得更连续、更实时的数据。");
            items.add(item);
        }
        return items;
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

    private String resolveOverallStatus(List<AIHealthInsightItem> items) {
        if (items.stream().anyMatch(item -> "high".equals(item.getSeverity()))) {
            return "alert";
        }
        if (items.stream().anyMatch(item -> "medium".equals(item.getSeverity()))) {
            return "warning";
        }
        return "stable";
    }

    private String buildSummary(List<AIHealthInsightItem> items) {
        if (items.isEmpty()) {
            return "当前没有发现明显异常，建议继续保持记录频率并按既定目标执行。";
        }

        String prompt = """
                你是健康管理应用中的主动健康管家。请基于以下待关注事项，用中文生成一段 80 到 140 字的总结。
                要求：
                1. 先指出最值得关注的风险。
                2. 再给出一条最重要的行动建议。
                3. 不要使用 Markdown 标题，不要编造列表以外的数据。

                待关注事项：
                """ + items.stream()
                .map(item -> "- [" + item.getSeverity() + "] " + item.getTitle() + "：" + item.getSummary()
                        + " 建议：" + item.getActionSuggestion())
                .collect(Collectors.joining("\n"));

        try {
            String response = aiService.generateAIResponse(prompt, null);
            if (response != null && !response.isBlank()) {
                return response.trim();
            }
        } catch (Exception e) {
            log.warn("生成AI主动预警总结失败，回退规则摘要: {}", e.getMessage());
        }

        AIHealthInsightItem topItem = items.get(0);
        return "当前最需要关注的是“" + topItem.getTitle() + "”。" + topItem.getSummary() + topItem.getActionSuggestion();
    }

    private boolean isFarOutsideRange(double value, double[] range) {
        if (value < range[0]) {
            return range[0] > 0 && (range[0] - value) / range[0] >= 0.2;
        }
        return range[1] > 0 && (value - range[1]) / range[1] >= 0.2;
    }

    private int severityRank(AIHealthInsightItem item) {
        if ("high".equals(item.getSeverity())) {
            return 3;
        }
        if ("medium".equals(item.getSeverity())) {
            return 2;
        }
        return 1;
    }

    private AIHealthGuardianResponse readCache(String cacheKey) {
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof AIHealthGuardianResponse response) {
                return response;
            }
        } catch (Exception e) {
            log.debug("读取AI主动预警缓存失败: {}", e.getMessage());
        }
        return null;
    }

    private void writeCache(String cacheKey, AIHealthGuardianResponse response) {
        try {
            redisTemplate.opsForValue().set(cacheKey, response, CACHE_TTL);
        } catch (Exception e) {
            log.debug("写入AI主动预警缓存失败: {}", e.getMessage());
        }
    }

    private String buildCacheKey(Long userId) {
        return "ai:guardian:insights:user:" + userId;
    }

    private String buildLockKey(Long userId) {
        return "ai_guardian_refresh_user_" + userId;
    }

    private String getMetricLabel(String type) {
        return LABELS.getOrDefault(type, type);
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
        return period;
    }

    private String safeUnit(String unit) {
        return unit == null || unit.isBlank() || "?".equals(unit) ? "" : unit;
    }

    private String formatDouble(double value) {
        return String.format("%.1f", value);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record GoalSnapshot(double currentValue, double progress, double remainingValue) {
    }

    private record DateRange(LocalDateTime start, LocalDateTime end) {
    }
}
