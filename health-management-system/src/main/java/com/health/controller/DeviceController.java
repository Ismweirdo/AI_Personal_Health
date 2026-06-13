package com.health.controller;

import com.health.common.Result;
import com.health.dto.*;
import com.health.service.DeviceService;
import com.health.service.HuaweiHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/device")
@Tag(name = "设备管理", description = "健康设备接入和管理接口")
public class DeviceController {

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private HuaweiHealthService huaweiHealthService;

    @Operation(summary = "注册新设备", description = "用户注册新的健康设备，获取设备ID和API密钥")
    @PostMapping("/register")
    public Result<DeviceRegistrationResponse> registerDevice(@Valid @RequestBody DeviceRegistrationRequest request) {
        return Result.success(deviceService.registerDevice(request));
    }

    @Operation(summary = "设备数据写入", description = "健康设备通过API密钥写入健康数据")
    @PostMapping("/data")
    public Result<DeviceDataResponse> receiveDeviceData(@Valid @RequestBody DeviceDataRequest request) {
        return Result.success(deviceService.receiveDeviceData(request));
    }

    @Operation(summary = "获取用户设备列表", description = "获取当前用户的所有设备信息")
    @GetMapping("/list")
    public Result<List<DeviceInfoResponse>> getUserDevices() {
        return Result.success(deviceService.getUserDevices());
    }

    @Operation(summary = "获取设备详细信息", description = "根据设备ID获取设备详细信息")
    @GetMapping("/info/{deviceId}")
    public Result<DeviceInfoResponse> getDeviceInfo(@PathVariable String deviceId) {
        return Result.success(deviceService.getDeviceInfo(deviceId));
    }

    @Operation(summary = "删除设备", description = "删除指定的设备")
    @DeleteMapping("/{deviceId}")
    public Result<Void> deleteDevice(@PathVariable String deviceId) {
        deviceService.deleteDevice(deviceId);
        return Result.success();
    }

    @Operation(summary = "更新设备状态", description = "更新设备状态（active/inactive/disabled）")
    @PutMapping("/{deviceId}/status")
    public Result<Void> updateDeviceStatus(
            @PathVariable String deviceId,
            @RequestParam String status) {
        deviceService.updateDeviceStatus(deviceId, status);
        return Result.success();
    }

    @Operation(summary = "验证API密钥", description = "验证API密钥是否有效")
    @GetMapping("/validate-key")
    public Result<Boolean> validateApiKey(@RequestParam String apiKey) {
        return Result.success(deviceService.validateApiKey(apiKey));
    }

    @Operation(summary = "绑定华为运动健康设备", description = "创建华为 Health Service Kit 授权绑定记录")
    @PostMapping("/huawei/bind")
    public Result<HuaweiBindResponse> bindHuaweiDevice(@Valid @RequestBody HuaweiBindRequest request) {
        return Result.success(huaweiHealthService.bind(request));
    }

    @Operation(summary = "同步华为运动健康数据", description = "同步华为穿戴设备健康数据，需先完成华为用户授权")
    @PostMapping("/huawei/{deviceId}/sync")
    public Result<HuaweiSyncResponse> syncHuaweiDevice(@PathVariable String deviceId) {
        return Result.success(huaweiHealthService.sync(deviceId));
    }
}
