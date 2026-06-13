package com.health.service.impl;

import com.health.dto.NotificationResponse;
import com.health.dto.ReminderRuleRequest;
import com.health.dto.ReminderRuleResponse;
import com.health.entity.FamilyGroup;
import com.health.entity.FamilyInvitation;
import com.health.entity.HealthData;
import com.health.entity.HealthGoal;
import com.health.entity.NotificationRecord;
import com.health.entity.ReminderRule;
import com.health.entity.User;
import com.health.repository.FamilyGroupRepository;
import com.health.repository.FamilyInvitationRepository;
import com.health.repository.HealthDataRepository;
import com.health.repository.HealthGoalRepository;
import com.health.repository.NotificationRecordRepository;
import com.health.repository.ReminderRuleRepository;
import com.health.repository.UserRepository;
import com.health.service.FamilyService;
import com.health.service.ReminderService;
import com.health.service.SmsService;
import com.health.utils.DistributedLock;
import com.health.utils.HealthMetricSupport;
import com.health.utils.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ReminderServiceImpl implements ReminderService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final String REMINDER_SCHEDULER_LOCK_KEY = "reminder:scheduler:scan";
    private static final long REMINDER_SCHEDULER_LOCK_WAIT_MILLIS = 200L;
    private static final long REMINDER_SCHEDULER_LOCK_LEASE_MILLIS = 25000L;
    private static final String ACTIVE_STATUS = "active";
    private static final String PENDING_STATUS = "pending";
    private static final String APPROVAL_PENDING_STATUS = "approval_pending";
    private static final String FAMILY_INVITATION_ACTION = "family_invitation";
    private static final String FAMILY_INVITATION_APPROVAL_ACTION = "family_invitation_approval";

    @Autowired
    private ReminderRuleRepository reminderRuleRepository;

    @Autowired
    private NotificationRecordRepository notificationRecordRepository;

    @Autowired
    private FamilyInvitationRepository familyInvitationRepository;

    @Autowired
    private FamilyGroupRepository familyGroupRepository;

    @Autowired
    private HealthGoalRepository healthGoalRepository;

    @Autowired
    private HealthDataRepository healthDataRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SmsService smsService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private DistributedLock distributedLock;

    @Autowired
    private FamilyService familyService;

    @Override
    public ReminderRuleResponse createRule(ReminderRuleRequest request) {
        return createRule(request, null);
    }

    @Override
    public ReminderRuleResponse createRule(ReminderRuleRequest request, Long targetUserId) {
        ReminderRule rule = new ReminderRule();
        rule.setUserId(resolveAccessibleUserId(targetUserId));
        applyRequest(rule, request);
        return toRuleResponse(reminderRuleRepository.save(rule));
    }

    @Override
    public ReminderRuleResponse updateRule(Long id, ReminderRuleRequest request) {
        return updateRule(id, request, null);
    }

    @Override
    public ReminderRuleResponse updateRule(Long id, ReminderRuleRequest request, Long targetUserId) {
        ReminderRule rule = getOwnedRule(id, targetUserId);
        applyRequest(rule, request);
        return toRuleResponse(reminderRuleRepository.save(rule));
    }

    @Override
    public ReminderRuleResponse toggleRuleEnabled(Long id, Boolean enabled) {
        return toggleRuleEnabled(id, enabled, null);
    }

    @Override
    public ReminderRuleResponse toggleRuleEnabled(Long id, Boolean enabled, Long targetUserId) {
        ReminderRule rule = getOwnedRule(id, targetUserId);
        boolean nextEnabled = enabled != null ? enabled : !Boolean.TRUE.equals(rule.getEnabled());
        rule.setEnabled(nextEnabled);
        rule.setNextTriggerAt(nextEnabled ? calculateNextTriggerAt(rule, LocalDateTime.now()) : null);
        if (!nextEnabled) {
            rule.setLastTriggeredAt(null);
        }
        return toRuleResponse(reminderRuleRepository.save(rule));
    }

    @Override
    public List<ReminderRuleResponse> getRules() {
        return getRules(null);
    }

    @Override
    public List<ReminderRuleResponse> getRules(Long targetUserId) {
        Long userId = resolveAccessibleUserId(targetUserId);
        return reminderRuleRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toRuleResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteRule(Long id) {
        deleteRule(id, null);
    }

    @Override
    public void deleteRule(Long id, Long targetUserId) {
        ReminderRule rule = getOwnedRule(id, targetUserId);
        reminderRuleRepository.delete(rule);
    }

    @Override
    public List<NotificationResponse> getNotifications() {
        Long userId = getCurrentUserId();
        ensureFamilyInvitationNotifications(userId);
        return notificationRecordRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toNotificationResponse)
                .collect(Collectors.toList());
    }

    @Override
    public NotificationResponse markNotificationRead(Long id) {
        NotificationRecord notification = notificationRecordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("通知不存在"));
        if (!notification.getUserId().equals(getCurrentUserId())) {
            throw new RuntimeException("无权修改此通知");
        }
        notification.setStatus("read");
        notification.setReadAt(LocalDateTime.now());
        return toNotificationResponse(notificationRecordRepository.save(notification));
    }

    @Override
    public void deleteNotification(Long id) {
        NotificationRecord notification = notificationRecordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("通知不存在"));
        if (!notification.getUserId().equals(getCurrentUserId())) {
            throw new RuntimeException("无权删除此通知");
        }
        notificationRecordRepository.delete(notification);
    }

    private void applyRequest(ReminderRule rule, ReminderRuleRequest request) {
        if (request == null || !StringUtils.hasText(request.getTitle())) {
            throw new IllegalArgumentException("提醒标题不能为空");
        }
        if (!StringUtils.hasText(request.getFrequency())) {
            throw new IllegalArgumentException("提醒频率不能为空");
        }

        String frequency = request.getFrequency().trim().toLowerCase();
        rule.setTitle(request.getTitle().trim());
        rule.setType(StringUtils.hasText(request.getType()) ? request.getType().trim() : "custom");
        rule.setMessage(StringUtils.hasText(request.getMessage()) ? request.getMessage().trim() : null);
        rule.setFrequency(frequency);
        rule.setRemindTime(resolveTime(request.getRemindTime()));
        rule.setRemindDate(StringUtils.hasText(request.getRemindDate()) ? LocalDate.parse(request.getRemindDate().trim()) : null);
        rule.setWeeklyDay(request.getWeeklyDay());
        rule.setEnabled(request.getEnabled() == null || request.getEnabled());
        rule.setNextTriggerAt(rule.getEnabled() ? calculateNextTriggerAt(rule, LocalDateTime.now()) : null);
    }

    private ReminderRule getOwnedRule(Long id, Long targetUserId) {
        ReminderRule rule = reminderRuleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("提醒规则不存在"));
        Long accessibleUserId = resolveAccessibleUserId(targetUserId);
        if (!rule.getUserId().equals(accessibleUserId)) {
            throw new RuntimeException("无权修改此提醒规则");
        }
        return rule;
    }

    @Scheduled(fixedDelay = 30000L, initialDelay = 15000L)
    public void generateDueNotificationsForAllUsers() {
        DistributedLock.Lock lock = distributedLock.acquire(
                REMINDER_SCHEDULER_LOCK_KEY,
                REMINDER_SCHEDULER_LOCK_WAIT_MILLIS,
                REMINDER_SCHEDULER_LOCK_LEASE_MILLIS,
                java.util.concurrent.TimeUnit.MILLISECONDS
        );
        if (lock == null) {
            log.debug("当前实例未获取到提醒调度锁，跳过本轮扫描");
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            List<ReminderRule> dueRules = reminderRuleRepository.findByEnabledTrueAndNextTriggerAtLessThanEqualOrderByNextTriggerAtAsc(now);
            if (dueRules.isEmpty()) {
                return;
            }
            log.info("检测到 {} 条到期提醒规则，开始自动生成通知", dueRules.size());
            processDueRules(dueRules, now);
        } finally {
            distributedLock.release(lock);
        }
    }

    private void generateDueNotifications(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<ReminderRule> dueRules = reminderRuleRepository.findByUserIdAndEnabledTrueAndNextTriggerAtLessThanEqual(userId, now);
        processDueRules(dueRules, now);
    }

    private void processDueRules(List<ReminderRule> dueRules, LocalDateTime now) {
        for (ReminderRule rule : dueRules) {
            LocalDateTime scheduledFor = rule.getNextTriggerAt();
            if (scheduledFor == null) {
                continue;
            }
            if (!notificationRecordRepository.existsByRuleIdAndScheduledFor(rule.getId(), scheduledFor)) {
                NotificationRecord notification = new NotificationRecord();
                notification.setUserId(rule.getUserId());
                notification.setRuleId(rule.getId());
                notification.setTitle(rule.getTitle());
                notification.setType(rule.getType());
                notification.setMessage(StringUtils.hasText(rule.getMessage()) ? rule.getMessage() : buildDefaultMessage(rule));
                notification.setScheduledFor(scheduledFor);
                notificationRecordRepository.save(notification);
                sendGoalReminderSmsIfNeeded(rule);
            }

            rule.setLastTriggeredAt(scheduledFor);
            if ("once".equals(rule.getFrequency())) {
                rule.setEnabled(false);
                rule.setNextTriggerAt(null);
            } else {
                rule.setNextTriggerAt(calculateNextTriggerAt(rule, scheduledFor.plusMinutes(1)));
            }
            reminderRuleRepository.save(rule);
        }
    }

    private ReminderRuleResponse toRuleResponse(ReminderRule rule) {
        ReminderRuleResponse response = new ReminderRuleResponse();
        response.setId(rule.getId());
        response.setTitle(rule.getTitle());
        response.setType(rule.getType());
        response.setMessage(rule.getMessage());
        response.setFrequency(rule.getFrequency());
        response.setRemindTime(rule.getRemindTime());
        response.setRemindDate(rule.getRemindDate() == null ? null : rule.getRemindDate().toString());
        response.setWeeklyDay(rule.getWeeklyDay());
        response.setEnabled(rule.getEnabled());
        response.setNextTriggerAt(formatDateTime(rule.getNextTriggerAt()));
        response.setLastTriggeredAt(formatDateTime(rule.getLastTriggeredAt()));
        response.setCreatedAt(formatDateTime(rule.getCreatedAt()));
        return response;
    }

    private NotificationResponse toNotificationResponse(NotificationRecord notification) {
        NotificationResponse response = new NotificationResponse();
        response.setId(notification.getId());
        response.setRuleId(notification.getRuleId());
        response.setTitle(notification.getTitle());
        response.setType(notification.getType());
        response.setMessage(notification.getMessage());
        response.setActionType(notification.getActionType());
        response.setActionRefId(notification.getActionRefId());
        response.setActionStatus(notification.getActionStatus());
        response.setStatus(notification.getStatus());
        response.setScheduledFor(formatDateTime(notification.getScheduledFor()));
        response.setReadAt(formatDateTime(notification.getReadAt()));
        response.setCreatedAt(formatDateTime(notification.getCreatedAt()));
        return response;
    }

    private void ensureFamilyInvitationNotifications(Long userId) {
        User currentUser = userRepository.findById(userId).orElse(null);
        if (currentUser != null && StringUtils.hasText(currentUser.getPhone())) {
            String phone = currentUser.getPhone().trim();
            familyInvitationRepository.findByInviteePhoneOrderByCreatedAtDesc(phone)
                    .stream()
                    .filter(invitation -> PENDING_STATUS.equals(invitation.getStatus()))
                    .filter(invitation -> invitation.getExpiresAt() == null || invitation.getExpiresAt().isAfter(LocalDateTime.now()))
                    .forEach(invitation -> ensureInviteeNotification(userId, invitation));
        }

        familyGroupRepository.findByCreatorUserIdAndStatusOrderByCreatedAtDesc(userId, ACTIVE_STATUS)
                .forEach(family -> familyInvitationRepository.findByFamilyIdOrderByCreatedAtDesc(family.getId())
                        .stream()
                        .filter(invitation -> APPROVAL_PENDING_STATUS.equals(invitation.getStatus()))
                        .forEach(invitation -> ensureApprovalNotification(userId, family, invitation)));
    }

    private void ensureInviteeNotification(Long userId, FamilyInvitation invitation) {
        if (notificationRecordRepository.existsByUserIdAndActionTypeAndActionRefId(
                userId, FAMILY_INVITATION_ACTION, invitation.getId())) {
            return;
        }
        FamilyGroup family = familyGroupRepository.findById(invitation.getFamilyId()).orElse(null);
        if (family == null || !ACTIVE_STATUS.equals(family.getStatus())) {
            return;
        }
        User inviter = userRepository.findById(invitation.getInviterUserId()).orElse(null);
        NotificationRecord notification = new NotificationRecord();
        notification.setUserId(userId);
        notification.setTitle("家庭组邀请");
        notification.setType(FAMILY_INVITATION_ACTION);
        notification.setMessage((inviter == null ? "家庭成员" : inviter.getUsername())
                + " 邀请你以" + roleLabel(invitation.getInviteeRole()) + "身份加入「" + family.getName() + "」。");
        notification.setActionType(FAMILY_INVITATION_ACTION);
        notification.setActionRefId(invitation.getId());
        notification.setActionStatus(PENDING_STATUS);
        notificationRecordRepository.save(notification);
    }

    private void ensureApprovalNotification(Long creatorUserId, FamilyGroup family, FamilyInvitation invitation) {
        if (notificationRecordRepository.existsByUserIdAndActionTypeAndActionRefId(
                creatorUserId, FAMILY_INVITATION_APPROVAL_ACTION, invitation.getId())) {
            return;
        }
        User inviter = userRepository.findById(invitation.getInviterUserId()).orElse(null);
        NotificationRecord notification = new NotificationRecord();
        notification.setUserId(creatorUserId);
        notification.setTitle("家庭邀请申请");
        notification.setType(FAMILY_INVITATION_APPROVAL_ACTION);
        notification.setMessage((inviter == null ? "家庭家长" : inviter.getUsername())
                + " 申请邀请手机号 " + invitation.getInviteePhone()
                + " 以" + roleLabel(invitation.getInviteeRole()) + "身份加入「" + family.getName() + "」。");
        notification.setActionType(FAMILY_INVITATION_APPROVAL_ACTION);
        notification.setActionRefId(invitation.getId());
        notification.setActionStatus(PENDING_STATUS);
        notificationRecordRepository.save(notification);
    }

    private String roleLabel(String role) {
        return "parent".equals(role) ? "家长" : "儿童";
    }

    private LocalDateTime calculateNextTriggerAt(ReminderRule rule, LocalDateTime baseline) {
        LocalTime time = LocalTime.parse(resolveTime(rule.getRemindTime()), TIME_FORMATTER);
        if ("weekly".equals(rule.getFrequency())) {
            if (rule.getWeeklyDay() == null) {
                throw new IllegalArgumentException("每周提醒需要设置星期几");
            }
            LocalDate candidateDate = baseline.toLocalDate();
            while (candidateDate.getDayOfWeek().getValue() != rule.getWeeklyDay()) {
                candidateDate = candidateDate.plusDays(1);
            }
            LocalDateTime candidate = LocalDateTime.of(candidateDate, time);
            if (candidate.isBefore(baseline)) {
                candidate = candidate.plusWeeks(1);
            }
            return candidate;
        }
        if ("once".equals(rule.getFrequency())) {
            if (rule.getRemindDate() == null) {
                throw new IllegalArgumentException("单次提醒需要设置提醒日期");
            }
            return LocalDateTime.of(rule.getRemindDate(), time);
        }

        LocalDateTime candidate = LocalDateTime.of(baseline.toLocalDate(), time);
        if (candidate.isBefore(baseline)) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    private String resolveTime(String time) {
        return StringUtils.hasText(time) ? LocalTime.parse(time.trim(), TIME_FORMATTER).format(TIME_FORMATTER) : "09:00";
    }

    private String buildDefaultMessage(ReminderRule rule) {
        return "请按时完成提醒事项：" + rule.getTitle();
    }

    private void sendGoalReminderSmsIfNeeded(ReminderRule rule) {
        if (!StringUtils.hasText(rule.getType()) || "custom".equalsIgnoreCase(rule.getType())) {
            return;
        }
        List<HealthGoal> goals = healthGoalRepository.findByUserIdAndTypeAndEnabledTrueOrderByCreatedAtDesc(
                rule.getUserId(),
                rule.getType()
        );
        if (goals.isEmpty()) {
            return;
        }

        HealthGoal goal = goals.get(0);
        GoalSnapshot snapshot = buildGoalSnapshot(rule.getUserId(), goal);
        if (snapshot.progress() >= 100D) {
            return;
        }

        userRepository.findById(rule.getUserId())
                .map(User::getPhone)
                .filter(StringUtils::hasText)
                .ifPresent(phone -> {
                    String message = rule.getTitle() + "：当前目标完成度 " + formatDouble(snapshot.progress())
                            + "%，还差 " + formatDouble(snapshot.remainingValue()) + safeUnit(goal.getUnit())
                            + "。请按计划及时完成。";
                    try {
                        smsService.sendHealthAlert(rule.getUserId(), phone, message);
                    } catch (Exception e) {
                        log.warn("发送目标未完成短信失败: userId={}, ruleId={}, error={}",
                                rule.getUserId(), rule.getId(), e.getMessage());
                    }
                });
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
        return new GoalSnapshot(currentValue, progress, remainingValue);
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

    private String safeUnit(String unit) {
        return unit == null || unit.isBlank() || "?".equals(unit) ? "" : unit;
    }

    private String formatDouble(double value) {
        return String.format("%.1f", value);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME_FORMATTER);
    }

    private Long getCurrentUserId() {
        Long userId = jwtUtils.getCurrentUserId();
        return userId == null ? 1L : userId;
    }

    private Long resolveAccessibleUserId(Long targetUserId) {
        return familyService.resolveAccessibleUserId(targetUserId);
    }

    private record GoalSnapshot(double currentValue, double progress, double remainingValue) {
    }

    private record DateRange(LocalDateTime start, LocalDateTime end) {
    }
}
