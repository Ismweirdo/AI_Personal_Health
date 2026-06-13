package com.health.service;

import com.health.dto.DeviceDataRequest;
import com.health.dto.DeviceDataResponse;
import com.health.dto.DeviceInfoResponse;
import com.health.dto.DeviceRegistrationRequest;
import com.health.dto.DeviceRegistrationResponse;

import java.util.List;

public interface DeviceService {

    DeviceRegistrationResponse registerDevice(DeviceRegistrationRequest request);

    DeviceDataResponse receiveDeviceData(DeviceDataRequest request);

    List<DeviceInfoResponse> getUserDevices();

    DeviceInfoResponse getDeviceInfo(String deviceId);

    void deleteDevice(String deviceId);

    void updateDeviceStatus(String deviceId, String status);

    String generateApiKey();

    boolean validateApiKey(String apiKey);
}