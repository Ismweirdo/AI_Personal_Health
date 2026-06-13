package com.health.service;

import com.health.dto.HealthDataRequest;
import com.health.dto.HealthDataImportResult;
import com.health.dto.HealthDataResponse;

import java.util.List;

public interface HealthDataService {

    HealthDataResponse addHealthData(HealthDataRequest request);

    HealthDataResponse addHealthData(HealthDataRequest request, Long targetUserId);

    HealthDataResponse updateHealthData(Long id, HealthDataRequest request);

    HealthDataResponse updateHealthData(Long id, HealthDataRequest request, Long targetUserId);

    List<HealthDataResponse> getHealthDataList(String type, String startDate, String endDate);

    List<HealthDataResponse> getHealthDataList(String type, String startDate, String endDate, Long targetUserId);

    List<HealthDataResponse> getHealthDataTrend(String type, String period);

    List<HealthDataResponse> getHealthDataTrend(String type, String period, Long targetUserId);

    HealthDataImportResult importHealthData(List<HealthDataRequest> requests);

    HealthDataImportResult importHealthData(List<HealthDataRequest> requests, Long targetUserId);

    String exportHealthData(String type, String startDate, String endDate);

    String exportHealthData(String type, String startDate, String endDate, Long targetUserId);

    String getImportTemplate();

    void deleteHealthData(Long id);

    void deleteHealthData(Long id, Long targetUserId);
}
