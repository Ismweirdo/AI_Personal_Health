package com.health.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HealthDataImportResult {

    private int successCount;
    private int failureCount;
    private List<HealthDataResponse> importedRecords = new ArrayList<>();
    private List<String> errors = new ArrayList<>();
}
