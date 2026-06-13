package com.health.service.impl;

import com.health.dto.*;
import com.health.entity.DeviceDataLog;
import com.health.entity.HealthData;
import com.health.entity.HealthDevice;
import com.health.repository.DeviceDataLogRepository;
import com.health.repository.HealthDataRepository;
import com.health.repository.HealthDeviceRepository;
import com.health.service.DeviceService;
import com.health.service.HealthAlertService;
import com.health.utils.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DeviceServiceImpl implements DeviceService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private HealthDeviceRepository deviceRepository;

    @Autowired
    private DeviceDataLogRepository dataLogRepository;

    @Autowired
    private HealthDataRepository healthDataRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private HealthAlertService healthAlertService;

    @Override
    @Transactional
    public DeviceRegistrationResponse registerDevice(DeviceRegistrationRequest request) {
        Long userId = getCurrentUserId();

        // 检查是否超过设备数量限制
        List<HealthDevice> existingDevices = deviceRepository.findByUserId(userId);
        if (existingDevices.size() >= 10) {
            throw new RuntimeException("设备数量超过限制，最多可注册10个设备");
        }

        // 生成设备ID和API密钥
        String deviceId = generateDeviceId();
        String apiKey = generateApiKey();

        // 创建设备记录
        HealthDevice device = new HealthDevice();
        device.setUserId(userId);
        device.setDeviceId(deviceId);
        device.setDeviceName(request.getDeviceName());
        device.setDeviceType(request.getDeviceType());
        device.setDeviceModel(request.getDeviceModel());
        device.setManufacturer(request.getManufacturer());
        device.setFirmwareVersion(request.getFirmwareVersion());
        device.setApiKey(apiKey);
        device.setStatus("active");
        device.setDescription(request.getDescription());

        HealthDevice savedDevice = deviceRepository.save(device);

        log.info("设备注册成功: userId={}, deviceId={}, deviceName={}", userId, deviceId, request.getDeviceName());

        return convertToRegistrationResponse(savedDevice, "设备注册成功");
    }

    @Override
    @Transactional
    public DeviceDataResponse receiveDeviceData(DeviceDataRequest request) {
        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();

        // 验证API密钥
        HealthDevice device = deviceRepository.findByApiKey(request.getApiKey())
                .orElseThrow(() -> new RuntimeException("无效的API密钥"));

        // 验证设备ID
        if (!device.getDeviceId().equals(request.getDeviceId())) {
            throw new RuntimeException("设备ID与API密钥不匹配");
        }

        // 检查设备状态
        if (!"active".equals(device.getStatus())) {
            throw new RuntimeException("设备未激活，无法接收数据");
        }

        // 更新设备最后活跃时间
        device.setLastActive(LocalDateTime.now());
        deviceRepository.save(device);

        // 处理数据点
        List<DeviceDataResponse.DataPointResult> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (DeviceDataRequest.DataPoint dataPoint : request.getData()) {
            DeviceDataResponse.DataPointResult result = processSingleDataPoint(
                    device, dataPoint, requestId);
            results.add(result);
            if (result.isSuccess()) {
                successCount++;
            } else {
                failureCount++;
            }
        }

        long processingTime = System.currentTimeMillis() - startTime;

        log.info("设备数据接收完成: deviceId={}, requestId={}, total={}, success={}, failure={}, time={}ms",
                device.getDeviceId(), requestId, request.getData().size(), successCount, failureCount, processingTime);

        return new DeviceDataResponse(
                requestId,
                device.getDeviceId(),
                device.getDeviceName(),
                LocalDateTime.now(),
                request.getData().size(),
                successCount,
                failureCount,
                results,
                "数据接收完成"
        );
    }

    @Override
    public List<DeviceInfoResponse> getUserDevices() {
        Long userId = getCurrentUserId();
        List<HealthDevice> devices = deviceRepository.findByUserId(userId);
        return devices.stream()
                .map(this::convertToInfoResponse)
                .collect(Collectors.toList());
    }

    @Override
    public DeviceInfoResponse getDeviceInfo(String deviceId) {
        Long userId = getCurrentUserId();
        HealthDevice device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new RuntimeException("设备不存在"));

        if (!device.getUserId().equals(userId)) {
            throw new RuntimeException("无权访问此设备信息");
        }

        return convertToInfoResponse(device);
    }

    @Override
    @Transactional
    public void deleteDevice(String deviceId) {
        Long userId = getCurrentUserId();
        HealthDevice device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new RuntimeException("设备不存在"));

        if (!device.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除此设备");
        }

        deviceRepository.delete(device);
        log.info("设备删除成功: userId={}, deviceId={}", userId, deviceId);
    }

    @Override
    @Transactional
    public void updateDeviceStatus(String deviceId, String status) {
        Long userId = getCurrentUserId();
        HealthDevice device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new RuntimeException("设备不存在"));

        if (!device.getUserId().equals(userId)) {
            throw new RuntimeException("无权修改此设备状态");
        }

        device.setStatus(status);
        deviceRepository.save(device);
        log.info("设备状态更新: userId={}, deviceId={}, status={}", userId, deviceId, status);
    }

    @Override
    public String generateApiKey() {
        return "HMS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 32).toUpperCase();
    }

    @Override
    public boolean validateApiKey(String apiKey) {
        return deviceRepository.findByApiKey(apiKey).isPresent();
    }

    private DeviceDataResponse.DataPointResult processSingleDataPoint(
            HealthDevice device, DeviceDataRequest.DataPoint dataPoint, String requestId) {

        long startTime = System.currentTimeMillis();
        DeviceDataResponse.DataPointResult result = new DeviceDataResponse.DataPointResult();
        result.setType(dataPoint.getType());
        result.setValue(dataPoint.getValue());
        result.setUnit(dataPoint.getUnit());
        result.setRecordDate(dataPoint.getRecordDate());
        result.setSuccess(false);

        try {
            // 验证数据类型
            validateDataType(dataPoint.getType());

            // 解析记录时间
            LocalDateTime recordDate = parseRecordDate(dataPoint.getRecordDate());

            // 创建健康数据记录
            HealthData healthData = new HealthData();
            healthData.setUserId(device.getUserId());
            healthData.setType(dataPoint.getType());
            healthData.setDataValue(dataPoint.getValue());
            healthData.setUnit(dataPoint.getUnit());
            healthData.setRecordDate(recordDate);
            healthData.setNotes(dataPoint.getNotes());

            HealthData savedHealthData = healthDataRepository.save(healthData);
            healthAlertService.handleAbnormalMetric(savedHealthData);

            // 记录成功日志
            logDeviceData(device, dataPoint, requestId, "success", null, System.currentTimeMillis() - startTime);

            result.setSuccess(true);
            result.setHealthDataId(savedHealthData.getId());

        } catch (Exception e) {
            String errorMessage = "数据处理失败: " + e.getMessage();
            result.setErrorMessage(errorMessage);

            // 记录失败日志
            logDeviceData(device, dataPoint, requestId, "failure", errorMessage, System.currentTimeMillis() - startTime);

            log.error("设备数据处理失败: deviceId={}, type={}, error={}",
                    device.getDeviceId(), dataPoint.getType(), e.getMessage());
        }

        return result;
    }

    private void logDeviceData(HealthDevice device, DeviceDataRequest.DataPoint dataPoint,
                               String requestId, String status, String errorMessage, long processingTime) {

        DeviceDataLog log = new DeviceDataLog();
        log.setDeviceId(device.getDeviceId());
        log.setUserId(device.getUserId());
        log.setDataType(dataPoint.getType());
        log.setDataValue(dataPoint.getValue());
        log.setUnit(dataPoint.getUnit());
        log.setRecordDate(parseRecordDate(dataPoint.getRecordDate()));
        log.setSource("device_api");
        log.setRequestId(requestId);
        log.setStatus(status);
        log.setErrorMessage(errorMessage);
        log.setProcessingTimeMs(processingTime);

        dataLogRepository.save(log);
    }

    private void validateDataType(String type) {
        List<String> validTypes = List.of("steps", "heart_rate", "sleep", "weight", "blood_pressure", "blood_sugar");
        if (!validTypes.contains(type)) {
            throw new IllegalArgumentException("无效的数据类型: " + type);
        }
    }

    private LocalDateTime parseRecordDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return LocalDateTime.now();
        }
        String trimmed = value.trim();
        try {
            if (trimmed.length() == 10) {
                return java.time.LocalDate.parse(trimmed).atStartOfDay();
            }
            return LocalDateTime.parse(trimmed, DATE_TIME_FORMATTER);
        } catch (Exception e) {
            throw new IllegalArgumentException("记录日期格式不正确，应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss");
        }
    }

    private String generateDeviceId() {
        return "DEV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private DeviceRegistrationResponse convertToRegistrationResponse(HealthDevice device, String message) {
        return new DeviceRegistrationResponse(
                device.getDeviceId(),
                device.getApiKey(),
                device.getDeviceName(),
                device.getDeviceType(),
                device.getDeviceModel(),
                device.getManufacturer(),
                device.getFirmwareVersion(),
                device.getStatus(),
                device.getCreatedAt(),
                message
        );
    }

    private DeviceInfoResponse convertToInfoResponse(HealthDevice device) {
        DeviceInfoResponse response = new DeviceInfoResponse();
        BeanUtils.copyProperties(device, response);
        return response;
    }

    private Long getCurrentUserId() {
        Long userId = jwtUtils.getCurrentUserId();
        return userId == null ? 1L : userId;
    }
}
