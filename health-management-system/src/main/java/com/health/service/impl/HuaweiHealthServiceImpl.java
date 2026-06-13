package com.health.service.impl;

import com.health.config.HuaweiHealthProperties;
import com.health.dto.HuaweiBindRequest;
import com.health.dto.HuaweiBindResponse;
import com.health.dto.HuaweiSyncResponse;
import com.health.entity.HealthDevice;
import com.health.repository.HealthDeviceRepository;
import com.health.service.HuaweiHealthService;
import com.health.utils.JwtUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class HuaweiHealthServiceImpl implements HuaweiHealthService {
    private final HealthDeviceRepository deviceRepository;
    private final JwtUtils jwtUtils;
    private final HuaweiHealthProperties properties;

    public HuaweiHealthServiceImpl(HealthDeviceRepository deviceRepository,
                                   JwtUtils jwtUtils,
                                   HuaweiHealthProperties properties) {
        this.deviceRepository = deviceRepository;
        this.jwtUtils = jwtUtils;
        this.properties = properties;
    }

    @Override
    @Transactional
    public HuaweiBindResponse bind(HuaweiBindRequest request) {
        Long userId = getCurrentUserId();
        String deviceId = "HUAWEI-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

        HealthDevice device = new HealthDevice();
        device.setUserId(userId);
        device.setDeviceId(deviceId);
        device.setDeviceName(StringUtils.hasText(request.getDeviceName()) ? request.getDeviceName().trim() : "华为运动健康");
        device.setDeviceType("huawei_health");
        device.setDeviceModel(StringUtils.hasText(request.getDeviceModel()) ? request.getDeviceModel().trim() : "Huawei wearable");
        device.setManufacturer("Huawei");
        device.setFirmwareVersion(null);
        device.setApiKey(generateApiKey());
        device.setStatus("pending_auth");
        device.setDescription("华为运动健康授权绑定，手机号：" + maskPhone(request.getPhone())
                + "。需通过华为 Health Service Kit 完成用户授权后同步可穿戴健康数据。");
        HealthDevice saved = deviceRepository.save(device);

        HuaweiBindResponse response = new HuaweiBindResponse();
        response.setDeviceId(saved.getDeviceId());
        response.setStatus(saved.getStatus());
        response.setAuthorizationUrl(buildAuthorizationUrl(saved.getDeviceId()));
        if (!properties.isConfigured()) {
            response.setMessage("已创建华为设备绑定，请先配置华为开发者应用参数后继续授权。");
            response.setNextAction("配置 HUAWEI_CLIENT_ID、HUAWEI_CLIENT_SECRET、HUAWEI_REDIRECT_URI 后重新发起授权。");
            return response;
        }
        response.setMessage("已创建华为设备绑定，请打开授权链接完成华为账号授权。");
        response.setNextAction("完成授权回调后可调用同步接口获取华为穿戴健康数据。");
        return response;
    }

    @Override
    public HuaweiSyncResponse sync(String deviceId) {
        Long userId = getCurrentUserId();
        HealthDevice device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new RuntimeException("设备不存在"));
        if (!device.getUserId().equals(userId)) {
            throw new RuntimeException("无权同步此设备");
        }

        HuaweiSyncResponse response = new HuaweiSyncResponse();
        response.setImportedCount(0);
        if (!properties.isConfigured()) {
            response.setStatus("pending_config");
            response.setMessage("华为 Health Service Kit 参数尚未配置，暂不能同步真实设备数据。");
            response.setNextAction("在环境变量中配置华为开发者应用参数，并完成用户授权。");
            return response;
        }
        if (!"active".equalsIgnoreCase(device.getStatus())) {
            response.setStatus("pending_auth");
            response.setMessage("该华为设备尚未完成用户授权，暂不能同步数据。");
            response.setNextAction("请通过授权链接完成华为账号授权后再同步。");
            return response;
        }

        response.setStatus("ready");
        response.setMessage("华为授权配置已就绪。当前版本保留真实 Health Service Kit 同步扩展点，未伪造健康数据。");
        response.setNextAction("接入华为授权回调与数据读取接口后，可将手环/手表数据写入健康数据表。");
        return response;
    }

    private String buildAuthorizationUrl(String state) {
        if (!properties.isConfigured()) {
            return null;
        }
        return UriComponentsBuilder.fromUriString(properties.getAuthUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("scope", properties.getScope())
                .queryParam("state", state)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
    }

    private String generateApiKey() {
        return "HMS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 32).toUpperCase();
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return "unknown";
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    private Long getCurrentUserId() {
        Long userId = jwtUtils.getCurrentUserId();
        return userId == null ? 1L : userId;
    }
}
