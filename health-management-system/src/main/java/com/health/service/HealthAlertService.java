package com.health.service;

import com.health.entity.HealthData;

public interface HealthAlertService {
    void handleAbnormalMetric(HealthData healthData);
}
