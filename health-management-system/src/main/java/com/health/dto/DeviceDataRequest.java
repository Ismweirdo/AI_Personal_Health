package com.health.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
public class DeviceDataRequest {

    @NotBlank(message = "设备ID不能为空")
    private String deviceId;

    @NotBlank(message = "API密钥不能为空")
    private String apiKey;

    @NotNull(message = "数据列表不能为空")
    private List<DataPoint> data;

    @Data
    public static class DataPoint {
        @NotBlank(message = "数据类型不能为空")
        private String type;

        @NotNull(message = "数据值不能为空")
        private Double value;

        private String unit;

        @NotBlank(message = "记录时间不能为空")
        private String recordDate;

        private String notes;
    }
}