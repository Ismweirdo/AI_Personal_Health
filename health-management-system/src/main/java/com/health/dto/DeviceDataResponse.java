package com.health.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceDataResponse {

    private String requestId;
    private String deviceId;
    private String deviceName;
    private LocalDateTime receivedAt;
    private int totalCount;
    private int successCount;
    private int failureCount;
    private List<DataPointResult> results;
    private String message;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPointResult {
        private String type;
        private Double value;
        private String unit;
        private String recordDate;
        private boolean success;
        private String errorMessage;
        private Long healthDataId;
    }
}