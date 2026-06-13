package com.health.service.impl;

import com.health.entity.HealthData;
import com.health.entity.NotificationRecord;
import com.health.entity.User;
import com.health.repository.NotificationRecordRepository;
import com.health.repository.UserRepository;
import com.health.service.HealthAlertService;
import com.health.service.SmsService;
import com.health.utils.HealthMetricSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
public class HealthAlertServiceImpl implements HealthAlertService {
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

    private final UserRepository userRepository;
    private final NotificationRecordRepository notificationRecordRepository;
    private final SmsService smsService;
    private final boolean smsAlertEnabled;

    public HealthAlertServiceImpl(UserRepository userRepository,
                                  NotificationRecordRepository notificationRecordRepository,
                                  SmsService smsService,
                                  @Value("${health-alert.sms-enabled:false}") boolean smsAlertEnabled) {
        this.userRepository = userRepository;
        this.notificationRecordRepository = notificationRecordRepository;
        this.smsService = smsService;
        this.smsAlertEnabled = smsAlertEnabled;
    }

    @Override
    public void handleAbnormalMetric(HealthData healthData) {
        if (healthData == null || healthData.getType() == null || healthData.getDataValue() == null) {
            return;
        }
        double[] range = HealthMetricSupport.getNormalRange(healthData.getType());
        if (range == null) {
            return;
        }
        double value = healthData.getDataValue();
        if (value >= range[0] && value <= range[1]) {
            return;
        }

        String label = LABELS.getOrDefault(healthData.getType(), healthData.getType());
        String unit = StringUtils.hasText(healthData.getUnit()) ? healthData.getUnit() : "";
        String direction = value > range[1] ? "偏高" : "偏低";
        String message = label + "为 " + format(value) + unit + "，" + direction
                + "，正常范围约 " + format(range[0]) + "-" + format(range[1]) + unit
                + "。请复测并结合身体状态判断，必要时及时就医。";

        NotificationRecord notification = new NotificationRecord();
        notification.setUserId(healthData.getUserId());
        notification.setTitle(label + "异常提醒");
        notification.setType("health_alert");
        notification.setMessage(message);
        notification.setScheduledFor(LocalDateTime.now());
        notificationRecordRepository.save(notification);

        if (!smsAlertEnabled) {
            return;
        }

        userRepository.findById(healthData.getUserId())
                .map(User::getPhone)
                .filter(StringUtils::hasText)
                .ifPresent(phone -> {
                    try {
                        smsService.sendHealthAlert(healthData.getUserId(), phone, message);
                    } catch (Exception e) {
                        log.warn("发送健康异常短信失败: userId={}, metric={}, error={}",
                                healthData.getUserId(), healthData.getType(), e.getMessage());
                    }
                });
    }

    private String format(double value) {
        return String.format("%.1f", value);
    }
}
