package com.health.service.impl;

import com.health.dto.HealthGoalRequest;
import com.health.dto.HealthGoalResponse;
import com.health.entity.HealthData;
import com.health.entity.HealthGoal;
import com.health.repository.HealthDataRepository;
import com.health.repository.HealthGoalRepository;
import com.health.service.FamilyService;
import com.health.service.HealthGoalService;
import com.health.utils.HealthMetricSupport;
import com.health.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class HealthGoalServiceImpl implements HealthGoalService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private HealthGoalRepository healthGoalRepository;

    @Autowired
    private HealthDataRepository healthDataRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private FamilyService familyService;

    @Override
    public HealthGoalResponse createGoal(HealthGoalRequest request) {
        return createGoal(request, null);
    }

    @Override
    public HealthGoalResponse createGoal(HealthGoalRequest request, Long targetUserId) {
        HealthGoal goal = new HealthGoal();
        goal.setUserId(resolveAccessibleUserId(targetUserId));
        applyRequest(goal, request);
        return toResponse(healthGoalRepository.save(goal));
    }

    @Override
    public HealthGoalResponse updateGoal(Long id, HealthGoalRequest request) {
        return updateGoal(id, request, null);
    }

    @Override
    public HealthGoalResponse updateGoal(Long id, HealthGoalRequest request, Long targetUserId) {
        HealthGoal goal = healthGoalRepository.findById(id).orElseThrow(() -> new RuntimeException("目标不存在"));
        Long accessibleUserId = resolveAccessibleUserId(targetUserId);
        if (!goal.getUserId().equals(accessibleUserId)) {
            throw new RuntimeException("无权修改此目标");
        }
        applyRequest(goal, request);
        return toResponse(healthGoalRepository.save(goal));
    }

    @Override
    public List<HealthGoalResponse> getGoals(Boolean enabledOnly) {
        return getGoals(enabledOnly, null);
    }

    @Override
    public List<HealthGoalResponse> getGoals(Boolean enabledOnly, Long targetUserId) {
        Long userId = resolveAccessibleUserId(targetUserId);
        List<HealthGoal> goals = Boolean.TRUE.equals(enabledOnly)
                ? healthGoalRepository.findByUserIdAndEnabledTrueOrderByCreatedAtDesc(userId)
                : healthGoalRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return goals.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public void deleteGoal(Long id) {
        deleteGoal(id, null);
    }

    @Override
    public void deleteGoal(Long id, Long targetUserId) {
        HealthGoal goal = healthGoalRepository.findById(id).orElseThrow(() -> new RuntimeException("目标不存在"));
        Long accessibleUserId = resolveAccessibleUserId(targetUserId);
        if (!goal.getUserId().equals(accessibleUserId)) {
            throw new RuntimeException("无权删除此目标");
        }
        healthGoalRepository.delete(goal);
    }

    private void applyRequest(HealthGoal goal, HealthGoalRequest request) {
        if (request == null || !StringUtils.hasText(request.getType())) {
            throw new IllegalArgumentException("目标类型不能为空");
        }
        if (request.getTargetValue() == null || request.getTargetValue() <= 0) {
            throw new IllegalArgumentException("目标值必须大于0");
        }
        if (!StringUtils.hasText(request.getPeriod())) {
            throw new IllegalArgumentException("目标周期不能为空");
        }

        goal.setType(request.getType().trim());
        goal.setTargetValue(request.getTargetValue());
        goal.setUnit(StringUtils.hasText(request.getUnit()) ? request.getUnit().trim() : null);
        goal.setPeriod(request.getPeriod().trim().toLowerCase());
        goal.setEnabled(request.getEnabled() == null || request.getEnabled());
    }

    private HealthGoalResponse toResponse(HealthGoal goal) {
        DateRange dateRange = getDateRange(goal.getPeriod());
        List<HealthData> records = healthDataRepository.findByUserIdAndTypeAndRecordDateBetween(
                goal.getUserId(),
                goal.getType(),
                dateRange.start(),
                dateRange.end()
        );
        double currentValue = calculateProgressValue(goal.getType(), records);
        double targetValue = goal.getTargetValue();
        double progress = targetValue <= 0 ? 0D : Math.min(100D, currentValue / targetValue * 100D);

        HealthGoalResponse response = new HealthGoalResponse();
        response.setId(goal.getId());
        response.setType(goal.getType());
        response.setTargetValue(targetValue);
        response.setUnit(goal.getUnit());
        response.setPeriod(goal.getPeriod());
        response.setEnabled(goal.getEnabled());
        response.setCurrentValue(round(currentValue));
        response.setProgress(round(progress));
        response.setRemainingValue(round(Math.max(0D, targetValue - currentValue)));
        response.setCreatedAt(goal.getCreatedAt() == null ? null : goal.getCreatedAt().format(DATE_TIME_FORMATTER));
        response.setUpdatedAt(goal.getUpdatedAt() == null ? null : goal.getUpdatedAt().format(DATE_TIME_FORMATTER));
        return response;
    }

    private double calculateProgressValue(String type, List<HealthData> records) {
        if (records.isEmpty()) {
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

    private DateRange getDateRange(String period) {
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

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private Long getCurrentUserId() {
        Long userId = jwtUtils.getCurrentUserId();
        return userId == null ? 1L : userId;
    }

    private Long resolveAccessibleUserId(Long targetUserId) {
        return familyService.resolveAccessibleUserId(targetUserId);
    }

    private record DateRange(LocalDateTime start, LocalDateTime end) {
    }
}
