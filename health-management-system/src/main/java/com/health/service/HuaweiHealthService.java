package com.health.service;

import com.health.dto.HuaweiBindRequest;
import com.health.dto.HuaweiBindResponse;
import com.health.dto.HuaweiSyncResponse;

public interface HuaweiHealthService {
    HuaweiBindResponse bind(HuaweiBindRequest request);

    HuaweiSyncResponse sync(String deviceId);
}
