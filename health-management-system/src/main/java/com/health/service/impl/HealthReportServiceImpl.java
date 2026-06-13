package com.health.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.dto.HealthGoalResponse;
import com.health.dto.HealthMetricReportItem;
import com.health.dto.HealthReportResponse;
import com.health.dto.HealthReportSnapshotSummaryResponse;
import com.health.entity.HealthData;
import com.health.entity.HealthReportSnapshot;
import com.health.repository.HealthDataRepository;
import com.health.repository.HealthReportSnapshotRepository;
import com.health.service.HealthGoalService;
import com.health.service.HealthReportService;
import com.health.utils.HealthMetricSupport;
import com.health.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class HealthReportServiceImpl implements HealthReportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SOURCE_LIVE = "live";
    private static final String SOURCE_SNAPSHOT = "snapshot";
    private static final String SUMMARY_METHOD = "daily_aggregation";
    private static final String AGGREGATION_PERIOD_TOTAL = "period_total";
    private static final String AGGREGATION_DAILY_AVERAGE = "daily_average";
    private static final Map<String, String> LABELS = Map.of(
            "steps", "步数",
            "heart_rate", "心率",
            "sleep", "睡眠",
            "weight", "体重",
            "blood_pressure", "血压",
            "blood_sugar", "血糖",
            "diet", "饮食",
            "exercise", "运动",
            "mood", "情绪"
    );

    @Autowired
    private HealthDataRepository healthDataRepository;

    @Autowired
    private HealthGoalService healthGoalService;

    @Autowired
    private HealthReportSnapshotRepository healthReportSnapshotRepository;

    @Autowired
    private JwtUtils jwtUtils;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public HealthReportResponse generateReport(String period) {
        String normalizedPeriod = normalizePeriod(period);
        DateRange dateRange = resolveDateRange(normalizedPeriod, LocalDate.now());
        Long userId = getCurrentUserId();

        HealthReportResponse response = buildLiveReport(userId, normalizedPeriod, dateRange);
        HealthReportSnapshot snapshot = saveSnapshot(userId, response);
        response.setSnapshotId(snapshot.getId());
        response.setSourceType(SOURCE_LIVE);
        response.setGeneratedAt(LocalDateTime.now().format(DATE_TIME_FORMATTER));
        return response;
    }

    @Override
    public List<HealthReportSnapshotSummaryResponse> getReportSnapshots(String period) {
        Long userId = getCurrentUserId();
        String normalizedPeriod = normalizePeriod(period);
        return healthReportSnapshotRepository.findByUserIdAndPeriodOrderByStartDateDesc(userId, normalizedPeriod).stream()
                .map(this::toSnapshotSummary)
                .collect(Collectors.toList());
    }

    @Override
    public HealthReportResponse getReportSnapshot(Long snapshotId) {
        Long userId = getCurrentUserId();
        HealthReportSnapshot snapshot = healthReportSnapshotRepository.findByIdAndUserId(snapshotId, userId)
                .orElseThrow(() -> new RuntimeException("报告快照不存在"));
        return toSnapshotResponse(snapshot);
    }

    private HealthReportResponse buildLiveReport(Long userId, String period, DateRange dateRange) {
        List<HealthData> records = healthDataRepository.findByUserIdAndRecordDateBetweenOrderByRecordDateAsc(
                userId,
                dateRange.startDateTime(),
                dateRange.endDateTime()
        );

        Map<String, List<HealthData>> grouped = records.stream()
                .collect(Collectors.groupingBy(HealthData::getType, LinkedHashMap::new, Collectors.toList()));

        HealthReportResponse response = new HealthReportResponse();
        response.setPeriod(period);
        response.setStartDate(dateRange.startDate().format(DATE_FORMATTER));
        response.setEndDate(dateRange.endDate().format(DATE_FORMATTER));
        response.setGeneratedAt(LocalDateTime.now().format(DATE_TIME_FORMATTER));
        response.setSourceType(SOURCE_LIVE);
        response.setSummaryMethod(SUMMARY_METHOD);

        grouped.entrySet().stream()
                .sorted(Comparator.comparing(entry -> LABELS.getOrDefault(entry.getKey(), entry.getKey())))
                .forEach(entry -> response.getMetrics().add(toMetricItem(entry.getKey(), entry.getValue())));

        List<HealthGoalResponse> goals = healthGoalService.getGoals(true).stream()
                .sorted(Comparator.comparing(HealthGoalResponse::getCreatedAt).reversed())
                .collect(Collectors.toList());
        response.setGoals(goals);

        buildHighlights(response, goals);
        buildSuggestions(response, goals);
        return response;
    }

    private HealthMetricReportItem toMetricItem(String type, List<HealthData> items) {
        List<DailyMetricPoint> dailyPoints = aggregateByDay(type, items);
        List<Double> values = dailyPoints.stream().map(DailyMetricPoint::value).toList();
        double average = values.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0D);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0D);
        double latest = dailyPoints.get(dailyPoints.size() - 1).value();
        double first = dailyPoints.get(0).value();
        double total = values.stream().mapToDouble(Double::doubleValue).sum();
        String aggregationMode = resolveAggregationMode(type);

        HealthMetricReportItem item = new HealthMetricReportItem();
        item.setType(type);
        item.setLabel(LABELS.getOrDefault(type, type));
        item.setUnit(resolveUnit(items));
        item.setAggregationMode(aggregationMode);
        item.setRecordCount(items.size());
        item.setActiveDays(dailyPoints.size());
        item.setSummaryValue(round(AGGREGATION_PERIOD_TOTAL.equals(aggregationMode) ? total : average));
        item.setLatestValue(round(latest));
        item.setAverageValue(round(average));
        item.setMinValue(round(min));
        item.setMaxValue(round(max));
        item.setTrend(latest > first ? "上升" : latest < first ? "下降" : "平稳");
        item.setStatus(resolveStatus(type, average));
        return item;
    }

    private List<DailyMetricPoint> aggregateByDay(String type, List<HealthData> items) {
        Map<LocalDate, List<HealthData>> groupedByDay = items.stream()
                .collect(Collectors.groupingBy(
                        item -> item.getRecordDate().toLocalDate(),
                        TreeMap::new,
                        Collectors.toList()
                ));

        return groupedByDay.entrySet().stream()
                .map(entry -> new DailyMetricPoint(entry.getKey(), aggregateDayValue(type, entry.getValue())))
                .collect(Collectors.toList());
    }

    private double aggregateDayValue(String type, List<HealthData> items) {
        if (HealthMetricSupport.isCumulative(type)) {
            return items.stream().mapToDouble(HealthData::getDataValue).sum();
        }
        return items.stream().mapToDouble(HealthData::getDataValue).average().orElse(0D);
    }

    private String resolveAggregationMode(String type) {
        return HealthMetricSupport.isCumulative(type) ? AGGREGATION_PERIOD_TOTAL : AGGREGATION_DAILY_AVERAGE;
    }

    private String resolveUnit(List<HealthData> items) {
        return items.stream()
                .map(HealthData::getUnit)
                .filter(unit -> unit != null && !unit.isBlank() && !"?".equals(unit))
                .reduce((first, second) -> second)
                .orElse(items.get(items.size() - 1).getUnit());
    }

    private void buildHighlights(HealthReportResponse response, List<HealthGoalResponse> goals) {
        if (response.getMetrics().isEmpty()) {
            response.getHighlights().add("当前周期暂无健康数据，建议先补充记录。");
        }
        for (HealthMetricReportItem metric : response.getMetrics()) {
            String unit = safeUnit(metric.getUnit());
            if (AGGREGATION_PERIOD_TOTAL.equals(metric.getAggregationMode())) {
                response.getHighlights().add(metric.getLabel() + "本周期累计 " + metric.getSummaryValue() + unit
                        + "，日均 " + metric.getAverageValue() + unit + "，活跃 " + metric.getActiveDays() + " 天。");
            } else {
                response.getHighlights().add(metric.getLabel() + "按天聚合后日均 " + metric.getAverageValue() + unit
                        + "，范围 " + metric.getMinValue() + "-" + metric.getMaxValue() + unit + "。");
            }
        }
        for (HealthGoalResponse goal : goals) {
            String scopeText = "daily".equalsIgnoreCase(goal.getPeriod()) ? "（按目标自身周期追踪）" : "";
            response.getHighlights().add(LABELS.getOrDefault(goal.getType(), goal.getType()) + scopeText + "目标完成度为 " + goal.getProgress() + "%。");
        }
    }

    private void buildSuggestions(HealthReportResponse response, List<HealthGoalResponse> goals) {
        for (HealthMetricReportItem metric : response.getMetrics()) {
            if ("偏高".equals(metric.getStatus()) || "偏低".equals(metric.getStatus())) {
                response.getSuggestions().add(metric.getLabel() + "按天聚合后存在" + metric.getStatus() + "趋势，建议关注近期变化并适当调整作息或饮食。");
            }
        }
        for (HealthGoalResponse goal : goals) {
            if (goal.getProgress() != null && goal.getProgress() < 100D) {
                String scopeText = "daily".equalsIgnoreCase(goal.getPeriod()) ? "日均" : "";
                response.getSuggestions().add(LABELS.getOrDefault(goal.getType(), goal.getType()) + scopeText + "距离目标还差 "
                        + goal.getRemainingValue() + (goal.getUnit() == null ? "" : goal.getUnit()) + "。");
            }
        }
        if (response.getSuggestions().isEmpty()) {
            response.getSuggestions().add("本周期数据整体稳定，建议继续保持当前健康习惯。");
        }
    }

    private String resolveStatus(String type, double average) {
        double[] range = HealthMetricSupport.getNormalRange(type);
        if (range == null) {
            return "正常";
        }
        if (average < range[0]) {
            return "偏低";
        }
        if (average > range[1]) {
            return "偏高";
        }
        return "正常";
    }

    private HealthReportSnapshot saveSnapshot(Long userId, HealthReportResponse response) {
        LocalDate startDate = LocalDate.parse(response.getStartDate(), DATE_FORMATTER);
        LocalDate endDate = LocalDate.parse(response.getEndDate(), DATE_FORMATTER);
        HealthReportSnapshot snapshot = healthReportSnapshotRepository.findByUserIdAndPeriodAndStartDateAndEndDate(
                        userId, response.getPeriod(), startDate, endDate)
                .orElseGet(HealthReportSnapshot::new);

        snapshot.setUserId(userId);
        snapshot.setPeriod(response.getPeriod());
        snapshot.setStartDate(startDate);
        snapshot.setEndDate(endDate);
        snapshot.setMetricsJson(writeJson(response.getMetrics()));
        snapshot.setGoalsJson(writeJson(response.getGoals()));
        snapshot.setHighlightsJson(writeJson(response.getHighlights()));
        snapshot.setSuggestionsJson(writeJson(response.getSuggestions()));
        return healthReportSnapshotRepository.save(snapshot);
    }

    private HealthReportSnapshotSummaryResponse toSnapshotSummary(HealthReportSnapshot snapshot) {
        HealthReportSnapshotSummaryResponse summary = new HealthReportSnapshotSummaryResponse();
        summary.setId(snapshot.getId());
        summary.setPeriod(snapshot.getPeriod());
        summary.setStartDate(snapshot.getStartDate().format(DATE_FORMATTER));
        summary.setEndDate(snapshot.getEndDate().format(DATE_FORMATTER));
        summary.setGeneratedAt(resolveSnapshotGeneratedAt(snapshot));
        summary.setMetricCount(readJsonSize(snapshot.getMetricsJson()));
        summary.setGoalCount(readJsonSize(snapshot.getGoalsJson()));
        return summary;
    }

    private HealthReportResponse toSnapshotResponse(HealthReportSnapshot snapshot) {
        HealthReportResponse response = new HealthReportResponse();
        response.setSnapshotId(snapshot.getId());
        response.setPeriod(snapshot.getPeriod());
        response.setStartDate(snapshot.getStartDate().format(DATE_FORMATTER));
        response.setEndDate(snapshot.getEndDate().format(DATE_FORMATTER));
        response.setGeneratedAt(resolveSnapshotGeneratedAt(snapshot));
        response.setSourceType(SOURCE_SNAPSHOT);
        response.setSummaryMethod(SUMMARY_METHOD);
        response.setMetrics(readMetrics(snapshot.getMetricsJson()));
        response.setGoals(readGoals(snapshot.getGoalsJson()));
        response.setHighlights(readStringList(snapshot.getHighlightsJson()));
        response.setSuggestions(readStringList(snapshot.getSuggestionsJson()));
        return response;
    }

    private List<HealthMetricReportItem> readMetrics(String json) {
        return readJson(json, new TypeReference<List<HealthMetricReportItem>>() {
        });
    }

    private List<HealthGoalResponse> readGoals(String json) {
        return readJson(json, new TypeReference<List<HealthGoalResponse>>() {
        });
    }

    private List<String> readStringList(String json) {
        return readJson(json, new TypeReference<List<String>>() {
        });
    }

    private <T> T readJson(String json, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("解析报告快照失败", e);
        }
    }

    private int readJsonSize(String json) {
        try {
            return objectMapper.readTree(json).size();
        } catch (JsonProcessingException e) {
            return 0;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("保存报告快照失败", e);
        }
    }

    private String resolveSnapshotGeneratedAt(HealthReportSnapshot snapshot) {
        LocalDateTime generatedAt = snapshot.getUpdatedAt() != null ? snapshot.getUpdatedAt() : snapshot.getCreatedAt();
        return generatedAt == null ? LocalDateTime.now().format(DATE_TIME_FORMATTER) : generatedAt.format(DATE_TIME_FORMATTER);
    }

    private String normalizePeriod(String period) {
        return "monthly".equalsIgnoreCase(period) ? "monthly" : "weekly";
    }

    private String safeUnit(String unit) {
        return unit == null || unit.isBlank() || "?".equals(unit) ? "" : unit;
    }

    private DateRange resolveDateRange(String period, LocalDate anchorDate) {
        if ("monthly".equals(period)) {
            LocalDate start = anchorDate.withDayOfMonth(1);
            LocalDate end = anchorDate.withDayOfMonth(anchorDate.lengthOfMonth());
            return new DateRange(start, end);
        }
        LocalDate start = anchorDate.with(DayOfWeek.MONDAY);
        LocalDate end = anchorDate.with(DayOfWeek.SUNDAY);
        return new DateRange(start, end);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private Long getCurrentUserId() {
        Long userId = jwtUtils.getCurrentUserId();
        return userId == null ? 1L : userId;
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
        private LocalDateTime startDateTime() {
            return startDate.atStartOfDay();
        }

        private LocalDateTime endDateTime() {
            return endDate.atTime(LocalTime.MAX.withNano(0));
        }
    }

    private record DailyMetricPoint(LocalDate date, double value) {
    }
}
