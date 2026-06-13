package com.health.service.impl;

import com.health.dto.AIActionContextSnapshot;
import com.health.dto.AIActionDraftResponse;
import com.health.dto.AIGoalDraft;
import com.health.dto.AIReminderDraft;
import com.health.dto.HealthGoalRequest;
import com.health.dto.HealthGoalResponse;
import com.health.dto.ReminderRuleRequest;
import com.health.dto.ReminderRuleResponse;
import com.health.entity.HealthData;
import com.health.entity.HealthDevice;
import com.health.entity.HealthGoal;
import com.health.entity.NotificationRecord;
import com.health.entity.ReminderRule;
import com.health.repository.HealthDataRepository;
import com.health.repository.HealthDeviceRepository;
import com.health.repository.HealthGoalRepository;
import com.health.repository.NotificationRecordRepository;
import com.health.repository.ReminderRuleRepository;
import com.health.service.AIAssistantActionService;
import com.health.service.HealthGoalService;
import com.health.service.ReminderService;
import com.health.utils.HealthMetricSupport;
import com.health.utils.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AIAssistantActionServiceImpl implements AIAssistantActionService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");
    private static final Pattern TIME_PATTERN = Pattern.compile("([01]?\\d|2[0-3]):([0-5]\\d)");
    private static final Pattern CHINESE_TIME_PATTERN = Pattern.compile("(凌晨|早上|上午|中午|下午|晚上)?\\s*(\\d{1,2})\\s*点\\s*(半|(\\d{1,2})分?)?");
    private static final Pattern FULL_DATE_PATTERN = Pattern.compile("(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})");
    private static final Pattern MONTH_DAY_PATTERN = Pattern.compile("(\\d{1,2})月(\\d{1,2})[日号]?");
    private static final List<String> GOAL_KEYWORDS = Arrays.asList("目标", "达成", "达到", "控制在", "保持在", "减到", "增到", "设一个目标", "计划");
    private static final List<String> REMINDER_KEYWORDS = Arrays.asList("提醒", "通知", "闹钟", "记得");
    private static final Map<String, String> METRIC_LABELS = Map.ofEntries(
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
    private static final Map<String, String> DEFAULT_UNITS = Map.ofEntries(
            Map.entry("steps", "步"),
            Map.entry("heart_rate", "bpm"),
            Map.entry("sleep", "小时"),
            Map.entry("weight", "kg"),
            Map.entry("blood_pressure", "mmHg"),
            Map.entry("blood_sugar", "mmol/L"),
            Map.entry("exercise", "分钟"),
            Map.entry("diet", "kcal"),
            Map.entry("mood", "分")
    );
    private static final Map<String, List<String>> METRIC_ALIASES = new LinkedHashMap<>();

    static {
        METRIC_ALIASES.put("steps", Arrays.asList("步数", "走路", "散步", "步行", "步"));
        METRIC_ALIASES.put("heart_rate", Arrays.asList("心率", "心跳", "脉搏"));
        METRIC_ALIASES.put("sleep", Arrays.asList("睡眠", "睡觉", "休息"));
        METRIC_ALIASES.put("weight", Arrays.asList("体重", "减肥", "减重", "增重", "瘦"));
        METRIC_ALIASES.put("blood_pressure", Arrays.asList("血压"));
        METRIC_ALIASES.put("blood_sugar", Arrays.asList("血糖"));
        METRIC_ALIASES.put("exercise", Arrays.asList("运动", "锻炼", "跑步", "健身"));
        METRIC_ALIASES.put("diet", Arrays.asList("饮食", "热量", "卡路里", "吃饭"));
        METRIC_ALIASES.put("mood", Arrays.asList("情绪", "心情"));
    }

    private final HealthDataRepository healthDataRepository;
    private final HealthGoalRepository healthGoalRepository;
    private final ReminderRuleRepository reminderRuleRepository;
    private final NotificationRecordRepository notificationRecordRepository;
    private final HealthDeviceRepository healthDeviceRepository;
    private final JwtUtils jwtUtils;
    private final HealthGoalService healthGoalService;
    private final ReminderService reminderService;

    public AIAssistantActionServiceImpl(HealthDataRepository healthDataRepository,
                                        HealthGoalRepository healthGoalRepository,
                                        ReminderRuleRepository reminderRuleRepository,
                                        NotificationRecordRepository notificationRecordRepository,
                                        HealthDeviceRepository healthDeviceRepository,
                                        JwtUtils jwtUtils,
                                        HealthGoalService healthGoalService,
                                        ReminderService reminderService) {
        this.healthDataRepository = healthDataRepository;
        this.healthGoalRepository = healthGoalRepository;
        this.reminderRuleRepository = reminderRuleRepository;
        this.notificationRecordRepository = notificationRecordRepository;
        this.healthDeviceRepository = healthDeviceRepository;
        this.jwtUtils = jwtUtils;
        this.healthGoalService = healthGoalService;
        this.reminderService = reminderService;
    }

    @Override
    public AIActionDraftResponse generateDraft(Long userId, String instruction) {
        if (!StringUtils.hasText(instruction)) {
            throw new IllegalArgumentException("操作指令不能为空");
        }

        Long currentUserId = resolveCurrentUserId(userId);
        LocalDateTime now = LocalDateTime.now();
        List<HealthData> healthData = healthDataRepository.findByUserIdAndRecordDateBetweenOrderByRecordDateAsc(
                currentUserId,
                now.minusDays(30),
                now
        );
        List<HealthGoal> goals = healthGoalRepository.findByUserIdAndEnabledTrueOrderByCreatedAtDesc(currentUserId);
        List<ReminderRule> reminders = reminderRuleRepository.findByUserIdOrderByCreatedAtDesc(currentUserId);
        List<NotificationRecord> notifications = notificationRecordRepository.findByUserIdOrderByCreatedAtDesc(currentUserId);
        List<HealthDevice> devices = healthDeviceRepository.findByUserId(currentUserId);

        AIActionDraftResponse response = new AIActionDraftResponse();
        response.setInstruction(instruction.trim());
        response.setContextSnapshot(buildContextSnapshot(healthData, goals, reminders, notifications, devices));

        String normalizedInstruction = normalizeText(instruction);
        AIGoalDraft goalDraft = buildGoalDraft(normalizedInstruction, healthData, response.getWarnings());
        AIReminderDraft reminderDraft = buildReminderDraft(normalizedInstruction, reminders, response.getWarnings());

        if (goalDraft == null && containsAny(normalizedInstruction, GOAL_KEYWORDS)) {
            response.getWarnings().add("已识别到目标意图，但未能可靠提取目标指标，请在草案中手动补充。");
        }
        if (reminderDraft == null && containsAny(normalizedInstruction, REMINDER_KEYWORDS)) {
            response.getWarnings().add("已识别到提醒意图，但未能完整提取时间或频率，请手动调整提醒草案。");
        }
        if (goalDraft == null && reminderDraft == null) {
            response.getWarnings().add("本次更像咨询类问题，没有自动生成目标或提醒草案。你可以明确写出“帮我创建目标”或“帮我设置提醒”。");
        }

        response.setGoalDraft(goalDraft);
        response.setReminderDraft(reminderDraft);
        response.setSummary(buildSummary(response.getContextSnapshot(), goalDraft, reminderDraft));
        return response;
    }

    private AIActionContextSnapshot buildContextSnapshot(List<HealthData> healthData,
                                                        List<HealthGoal> goals,
                                                        List<ReminderRule> reminders,
                                                        List<NotificationRecord> notifications,
                                                        List<HealthDevice> devices) {
        AIActionContextSnapshot snapshot = new AIActionContextSnapshot();
        snapshot.setHealthRecordCount(healthData.size());
        snapshot.setActiveGoalCount(goals.size());
        snapshot.setReminderCount(reminders.size());
        snapshot.setEnabledReminderCount((int) reminders.stream().filter(item -> Boolean.TRUE.equals(item.getEnabled())).count());
        snapshot.setUnreadNotificationCount((int) notifications.stream().filter(item -> "unread".equalsIgnoreCase(item.getStatus())).count());
        snapshot.setDeviceCount(devices.size());

        Map<String, HealthData> latestByType = new LinkedHashMap<>();
        healthData.stream()
                .sorted(Comparator.comparing(HealthData::getRecordDate).reversed())
                .forEach(item -> latestByType.putIfAbsent(item.getType(), item));

        latestByType.values().stream()
                .limit(4)
                .forEach(item -> snapshot.getLatestMetricSummaries().add(
                        getMetricLabel(item.getType()) + " "
                                + formatDouble(item.getDataValue())
                                + resolveUnit(item.getType(), item.getUnit())
                                + "（" + item.getRecordDate().format(DATE_TIME_FORMATTER) + "）"
                ));
        return snapshot;
    }

    private AIGoalDraft buildGoalDraft(String instruction, List<HealthData> healthData, List<String> warnings) {
        String metricType = resolveMetricType(instruction);
        if (metricType == null || (!containsAny(instruction, GOAL_KEYWORDS) && !instruction.contains("目标"))) {
            return null;
        }

        double currentValue = calculateCurrentValue(metricType, healthData, resolveGoalPeriod(instruction));
        Double targetValue = extractNumber(instruction);
        if (targetValue == null || targetValue <= 0) {
            targetValue = suggestTargetValue(metricType, currentValue);
            warnings.add("未识别明确目标值，已根据当前数据生成建议目标，你可以继续修改。");
        }

        AIGoalDraft draft = new AIGoalDraft();
        draft.setType(metricType);
        draft.setTypeLabel(getMetricLabel(metricType));
        draft.setTargetValue(round(targetValue));
        draft.setUnit(resolveGoalUnit(metricType, instruction));
        draft.setPeriod(resolveGoalPeriod(instruction));
        draft.setEnabled(true);
        draft.setCurrentValue(round(currentValue));
        draft.setSuggestionReason("已结合你最近的" + getMetricLabel(metricType) + "数据生成目标草案，创建后可以在目标页继续调整。");
        return draft;
    }

    private AIReminderDraft buildReminderDraft(String instruction, List<ReminderRule> reminders, List<String> warnings) {
        if (!containsAny(instruction, REMINDER_KEYWORDS) && !containsReminderTimeHint(instruction)) {
            return null;
        }

        AIReminderDraft draft = new AIReminderDraft();
        String metricType = resolveMetricType(instruction);
        draft.setType(metricType == null ? "custom" : metricType);
        draft.setFrequency(resolveReminderFrequency(instruction));
        draft.setRemindTime(resolveReminderTime(instruction));
        draft.setWeeklyDay(resolveWeeklyDay(instruction));
        draft.setRemindDate(resolveReminderDate(instruction, draft.getFrequency()));
        draft.setTitle(resolveReminderTitle(instruction, metricType));
        draft.setMessage(resolveReminderMessage(instruction, draft.getTitle()));
        draft.setEnabled(true);

        if (!StringUtils.hasText(draft.getRemindTime())) {
            draft.setRemindTime("09:00");
            warnings.add("未识别明确提醒时间，已默认设置为 09:00。");
        }
        if ("weekly".equals(draft.getFrequency()) && draft.getWeeklyDay() == null) {
            draft.setWeeklyDay(LocalDate.now().getDayOfWeek().getValue());
            warnings.add("未识别到具体星期，已默认使用今天对应的星期。");
        }
        if ("once".equals(draft.getFrequency()) && !StringUtils.hasText(draft.getRemindDate())) {
            draft.setRemindDate(LocalDate.now().plusDays(1).toString());
            warnings.add("未识别到具体日期，已默认设置为明天。");
        }

        long enabledCount = reminders.stream().filter(item -> Boolean.TRUE.equals(item.getEnabled())).count();
        draft.setSuggestionReason("你当前已有 " + enabledCount + " 条启用中的提醒，这份草案会在创建后接入现有通知链路。");
        return draft;
    }

    private String buildSummary(AIActionContextSnapshot snapshot, AIGoalDraft goalDraft, AIReminderDraft reminderDraft) {
        List<String> parts = new ArrayList<>();
        parts.add("已读取近30天 " + snapshot.getHealthRecordCount() + " 条健康记录");
        parts.add(snapshot.getActiveGoalCount() + " 个启用目标");
        parts.add(snapshot.getReminderCount() + " 条提醒");
        if (snapshot.getDeviceCount() > 0) {
            parts.add(snapshot.getDeviceCount() + " 台设备数据");
        }

        StringBuilder summary = new StringBuilder(String.join("，", parts)).append("。");
        if (goalDraft != null) {
            summary.append(" 已生成 ").append(goalDraft.getTypeLabel()).append("目标草案");
        }
        if (reminderDraft != null) {
            if (goalDraft != null) {
                summary.append("，并生成");
            } else {
                summary.append(" 已生成");
            }
            summary.append(resolveReminderFrequencyLabel(reminderDraft.getFrequency())).append("提醒草案");
        }
        summary.append("，确认后即可直接创建。");
        return summary.toString();
    }

    private double calculateCurrentValue(String metricType, List<HealthData> healthData, String period) {
        DateRange range = resolveGoalDateRange(period);
        List<HealthData> records = healthData.stream()
                .filter(item -> metricType.equals(item.getType()))
                .filter(item -> !item.getRecordDate().isBefore(range.start()) && !item.getRecordDate().isAfter(range.end()))
                .collect(Collectors.toList());
        if (records.isEmpty()) {
            return 0D;
        }

        Map<LocalDate, List<HealthData>> groupedByDay = records.stream()
                .collect(Collectors.groupingBy(item -> item.getRecordDate().toLocalDate(), TreeMap::new, Collectors.toList()));
        List<Double> dailyValues = groupedByDay.values().stream()
                .map(items -> aggregateDayValue(metricType, items))
                .collect(Collectors.toList());

        if (HealthMetricSupport.isCumulative(metricType)) {
            return dailyValues.stream().mapToDouble(Double::doubleValue).sum();
        }
        return dailyValues.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
    }

    private double aggregateDayValue(String metricType, List<HealthData> items) {
        if (HealthMetricSupport.isCumulative(metricType)) {
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

    private String resolveMetricType(String instruction) {
        for (Map.Entry<String, List<String>> entry : METRIC_ALIASES.entrySet()) {
            for (String alias : entry.getValue()) {
                if (instruction.contains(alias.toLowerCase(Locale.ROOT))) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private Double extractNumber(String instruction) {
        Matcher matcher = NUMBER_PATTERN.matcher(instruction);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private String resolveGoalUnit(String metricType, String instruction) {
        if (instruction.contains("公斤")) {
            return "kg";
        }
        if (instruction.contains("小时")) {
            return "小时";
        }
        if (instruction.contains("分钟")) {
            return "分钟";
        }
        if (instruction.contains("千卡") || instruction.contains("kcal")) {
            return "kcal";
        }
        return DEFAULT_UNITS.getOrDefault(metricType, "");
    }

    private String resolveGoalPeriod(String instruction) {
        if (instruction.contains("每周") || instruction.contains("一周") || instruction.contains("周目标")) {
            return "weekly";
        }
        if (instruction.contains("每月") || instruction.contains("月目标") || instruction.contains("一个月")) {
            return "monthly";
        }
        return "daily";
    }

    private double suggestTargetValue(String metricType, double currentValue) {
        if (currentValue <= 0) {
            return switch (metricType) {
                case "steps" -> 8000D;
                case "sleep" -> 8D;
                case "weight" -> 60D;
                case "heart_rate" -> 80D;
                case "blood_pressure" -> 120D;
                case "blood_sugar" -> 6D;
                case "exercise" -> 30D;
                case "diet" -> 1800D;
                case "mood" -> 4D;
                default -> 1D;
            };
        }
        if ("weight".equals(metricType)) {
            return Math.max(1D, round(currentValue - 2D));
        }
        if ("heart_rate".equals(metricType) || "blood_pressure".equals(metricType) || "blood_sugar".equals(metricType)) {
            return round(currentValue);
        }
        return round(currentValue * 1.15D);
    }

    private String resolveReminderFrequency(String instruction) {
        if (instruction.contains("每周") || instruction.contains("周一") || instruction.contains("周二")
                || instruction.contains("周三") || instruction.contains("周四") || instruction.contains("周五")
                || instruction.contains("周六") || instruction.contains("周日") || instruction.contains("星期")) {
            return "weekly";
        }
        if (instruction.contains("今天") || instruction.contains("明天") || instruction.contains("后天")
                || FULL_DATE_PATTERN.matcher(instruction).find() || MONTH_DAY_PATTERN.matcher(instruction).find()) {
            return "once";
        }
        return "daily";
    }

    private String resolveReminderTime(String instruction) {
        Matcher matcher = TIME_PATTERN.matcher(instruction);
        if (matcher.find()) {
            return String.format("%02d:%02d", Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        }

        Matcher chineseMatcher = CHINESE_TIME_PATTERN.matcher(instruction);
        if (chineseMatcher.find()) {
            String prefix = Optional.ofNullable(chineseMatcher.group(1)).orElse("");
            int hour = Integer.parseInt(chineseMatcher.group(2));
            String halfOrMinute = chineseMatcher.group(3);
            String minuteText = chineseMatcher.group(4);
            int minute = 0;
            if ("半".equals(halfOrMinute)) {
                minute = 30;
            } else if (StringUtils.hasText(minuteText)) {
                minute = Integer.parseInt(minuteText);
            }
            if (("下午".equals(prefix) || "晚上".equals(prefix)) && hour < 12) {
                hour += 12;
            }
            if ("中午".equals(prefix) && hour < 11) {
                hour += 12;
            }
            return String.format("%02d:%02d", hour, minute);
        }
        return null;
    }

    private Integer resolveWeeklyDay(String instruction) {
        if (instruction.contains("周一") || instruction.contains("星期一")) {
            return 1;
        }
        if (instruction.contains("周二") || instruction.contains("星期二")) {
            return 2;
        }
        if (instruction.contains("周三") || instruction.contains("星期三")) {
            return 3;
        }
        if (instruction.contains("周四") || instruction.contains("星期四")) {
            return 4;
        }
        if (instruction.contains("周五") || instruction.contains("星期五")) {
            return 5;
        }
        if (instruction.contains("周六") || instruction.contains("星期六")) {
            return 6;
        }
        if (instruction.contains("周日") || instruction.contains("星期日") || instruction.contains("星期天")) {
            return 7;
        }
        return null;
    }

    private String resolveReminderDate(String instruction, String frequency) {
        if (!"once".equals(frequency)) {
            return null;
        }
        LocalDate today = LocalDate.now();
        if (instruction.contains("今天")) {
            return today.toString();
        }
        if (instruction.contains("明天")) {
            return today.plusDays(1).toString();
        }
        if (instruction.contains("后天")) {
            return today.plusDays(2).toString();
        }

        Matcher fullDateMatcher = FULL_DATE_PATTERN.matcher(instruction);
        if (fullDateMatcher.find()) {
            return LocalDate.of(
                    Integer.parseInt(fullDateMatcher.group(1)),
                    Integer.parseInt(fullDateMatcher.group(2)),
                    Integer.parseInt(fullDateMatcher.group(3))
            ).toString();
        }

        Matcher monthDayMatcher = MONTH_DAY_PATTERN.matcher(instruction);
        if (monthDayMatcher.find()) {
            int year = today.getYear();
            LocalDate candidate = LocalDate.of(year,
                    Integer.parseInt(monthDayMatcher.group(1)),
                    Integer.parseInt(monthDayMatcher.group(2)));
            if (candidate.isBefore(today)) {
                candidate = candidate.plusYears(1);
            }
            return candidate.toString();
        }
        return null;
    }

    private String resolveReminderTitle(String instruction, String metricType) {
        Matcher matcher = Pattern.compile("提醒我([^，。！？,!?]{1,20})").matcher(instruction);
        if (matcher.find() && StringUtils.hasText(matcher.group(1))) {
            return matcher.group(1).trim() + "提醒";
        }
        if (metricType != null) {
            return getMetricLabel(metricType) + "提醒";
        }
        return "健康提醒";
    }

    private String resolveReminderMessage(String instruction, String title) {
        if (instruction.contains("记得")) {
            return instruction.trim();
        }
        return "请按时完成：" + title.replace("提醒", "");
    }

    private boolean containsReminderTimeHint(String instruction) {
        return instruction.contains("点") || instruction.contains(":") || instruction.contains("明天") || instruction.contains("今天");
    }

    private boolean containsAny(String text, List<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }

    private String normalizeText(String input) {
        return input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
    }

    private String getMetricLabel(String metricType) {
        return METRIC_LABELS.getOrDefault(metricType, metricType);
    }

    private String resolveUnit(String metricType, String unit) {
        if (StringUtils.hasText(unit) && !"?".equals(unit)) {
            return unit.trim();
        }
        return DEFAULT_UNITS.getOrDefault(metricType, "");
    }

    private String resolveReminderFrequencyLabel(String frequency) {
        return switch (frequency) {
            case "weekly" -> "每周";
            case "once" -> "单次";
            default -> "每日";
        };
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String formatDouble(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private Long resolveCurrentUserId(Long userId) {
        Long currentUserId = jwtUtils.getCurrentUserId();
        if (currentUserId != null) {
            return currentUserId;
        }
        return userId == null ? 1L : userId;
    }

    @Override
    public Map<String, Object> executeAction(Long userId, String instruction) {
        if (!StringUtils.hasText(instruction)) {
            throw new IllegalArgumentException("操作指令不能为空");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("instruction", instruction.trim());

        try {
            Long currentUserId = resolveCurrentUserId(userId);
            String normalizedInstruction = normalizeText(instruction);

            boolean hasGoalIntent = containsAny(normalizedInstruction, GOAL_KEYWORDS);
            boolean hasReminderIntent = containsAny(normalizedInstruction, REMINDER_KEYWORDS) || containsReminderTimeHint(normalizedInstruction);

            List<String> executedActions = new ArrayList<>();
            List<String> messages = new ArrayList<>();

            if (hasGoalIntent) {
                HealthGoalResponse goalResponse = createGoalFromDraft(currentUserId, instruction);
                if (goalResponse != null) {
                    result.put("goal", goalResponse);
                    executedActions.add("goal");
                    messages.add("已创建" + goalResponse.getTypeLabel() + "目标，目标值：" + goalResponse.getTargetValue() + goalResponse.getUnit());
                }
            }

            if (hasReminderIntent) {
                ReminderRuleResponse reminderResponse = createReminderFromDraft(currentUserId, instruction);
                if (reminderResponse != null) {
                    result.put("reminder", reminderResponse);
                    executedActions.add("reminder");
                    messages.add("已创建" + resolveReminderFrequencyLabel(reminderResponse.getFrequency()) + "提醒：" + reminderResponse.getTitle());
                }
            }

            if (executedActions.isEmpty()) {
                messages.add("未识别到目标或提醒意图，请明确使用'创建目标'或'设置提醒'等关键词");
            } else {
                result.put("success", true);
            }

            result.put("executedActions", executedActions);
            result.put("messages", messages);
            result.put("summary", String.join("；", messages));

            log.info("AI assistant executed action for user {}: {}", currentUserId, executedActions);
            return result;

        } catch (Exception e) {
            log.error("AI assistant execute action failed: {}", e.getMessage(), e);
            result.put("error", e.getMessage());
            return result;
        }
    }

    @Override
    public HealthGoalResponse createGoalFromDraft(Long userId, String instruction) {
        if (!StringUtils.hasText(instruction)) {
            throw new IllegalArgumentException("操作指令不能为空");
        }

        Long currentUserId = resolveCurrentUserId(userId);
        LocalDateTime now = LocalDateTime.now();
        List<HealthData> healthData = healthDataRepository.findByUserIdAndRecordDateBetweenOrderByRecordDateAsc(
                currentUserId,
                now.minusDays(30),
                now
        );

        String normalizedInstruction = normalizeText(instruction);
        AIGoalDraft goalDraft = buildGoalDraft(normalizedInstruction, healthData, new ArrayList<>());

        if (goalDraft == null) {
            throw new IllegalArgumentException("未能从指令中提取有效的目标信息，请包含目标类型和目标值");
        }

        HealthGoalRequest request = new HealthGoalRequest();
        request.setType(goalDraft.getType());
        request.setTargetValue(goalDraft.getTargetValue());
        request.setUnit(goalDraft.getUnit());
        request.setPeriod(goalDraft.getPeriod());
        request.setEnabled(goalDraft.getEnabled());

        return healthGoalService.createGoal(request);
    }

    @Override
    public ReminderRuleResponse createReminderFromDraft(Long userId, String instruction) {
        if (!StringUtils.hasText(instruction)) {
            throw new IllegalArgumentException("操作指令不能为空");
        }

        Long currentUserId = resolveCurrentUserId(userId);
        List<ReminderRule> reminders = reminderRuleRepository.findByUserIdOrderByCreatedAtDesc(currentUserId);

        String normalizedInstruction = normalizeText(instruction);
        AIReminderDraft reminderDraft = buildReminderDraft(normalizedInstruction, reminders, new ArrayList<>());

        if (reminderDraft == null) {
            throw new IllegalArgumentException("未能从指令中提取有效的提醒信息，请包含提醒时间或频率");
        }

        ReminderRuleRequest request = new ReminderRuleRequest();
        request.setTitle(reminderDraft.getTitle());
        request.setType(reminderDraft.getType());
        request.setMessage(reminderDraft.getMessage());
        request.setFrequency(reminderDraft.getFrequency());
        request.setRemindTime(reminderDraft.getRemindTime());
        request.setRemindDate(reminderDraft.getRemindDate());
        request.setWeeklyDay(reminderDraft.getWeeklyDay());
        request.setEnabled(reminderDraft.getEnabled());

        return reminderService.createRule(request);
    }

    private record DateRange(LocalDateTime start, LocalDateTime end) {
    }
}
