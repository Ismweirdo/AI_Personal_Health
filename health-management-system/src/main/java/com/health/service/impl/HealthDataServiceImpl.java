package com.health.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.health.dto.HealthDataImportResult;
import com.health.dto.HealthDataRequest;
import com.health.dto.HealthDataResponse;
import com.health.entity.HealthData;
import com.health.repository.HealthDataRepository;
import com.health.service.FamilyService;
import com.health.service.HealthAlertService;
import com.health.service.HealthDataService;
import com.health.utils.CacheUtils;
import com.health.utils.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class HealthDataServiceImpl implements HealthDataService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String HEALTH_DATA_CACHE_PREFIX = "health:data:";
    private static final int CACHE_EXPIRATION = 5;

    @Autowired
    private HealthDataRepository healthDataRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private CacheUtils redisUtils;

    @Autowired
    private HealthAlertService healthAlertService;

    @Autowired
    private FamilyService familyService;

    @Override
    public HealthDataResponse addHealthData(HealthDataRequest request) {
        return addHealthData(request, null);
    }

    @Override
    public HealthDataResponse addHealthData(HealthDataRequest request, Long targetUserId) {
        Long userId = resolveAccessibleUserId(targetUserId);
        HealthData saved = saveHealthData(null, userId, request);
        healthAlertService.handleAbnormalMetric(saved);
        clearHealthDataCache(userId, saved.getType());
        return convertToResponse(saved);
    }

    @Override
    public HealthDataResponse updateHealthData(Long id, HealthDataRequest request) {
        return updateHealthData(id, request, null);
    }

    @Override
    public HealthDataResponse updateHealthData(Long id, HealthDataRequest request, Long targetUserId) {
        Long userId = resolveAccessibleUserId(targetUserId);
        HealthData saved = saveHealthData(id, userId, request);
        healthAlertService.handleAbnormalMetric(saved);
        clearHealthDataCache(userId, saved.getType());
        return convertToResponse(saved);
    }

    @Override
    public List<HealthDataResponse> getHealthDataList(String type, String startDate, String endDate) {
        return getHealthDataList(type, startDate, endDate, null);
    }

    @Override
    public List<HealthDataResponse> getHealthDataList(String type, String startDate, String endDate, Long targetUserId) {
        Long userId = resolveAccessibleUserId(targetUserId);
        String normalizedType = normalizeType(type);
        String cacheKey = buildCacheKey(userId, normalizedType, startDate, endDate);

        try {
            String cachedData = redisUtils.get(cacheKey);
            if (cachedData != null) {
                ObjectMapper objectMapper = new ObjectMapper();
                return objectMapper.readValue(cachedData, new TypeReference<List<HealthDataResponse>>() {});
            }
        } catch (Exception e) {
            log.info("读取健康数据缓存失败: {}", e.getMessage());
        }

        LocalDateTime start = parseQueryStart(startDate);
        LocalDateTime end = parseQueryEnd(endDate);
        List<HealthData> healthDataList = queryHealthData(userId, normalizedType, start, end);
        List<HealthDataResponse> responseList = healthDataList.stream().map(this::convertToResponse).collect(Collectors.toList());

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            redisUtils.set(cacheKey, objectMapper.writeValueAsString(responseList), CACHE_EXPIRATION, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.info("写入健康数据缓存失败: {}", e.getMessage());
        }

        return responseList;
    }

    @Override
    public List<HealthDataResponse> getHealthDataTrend(String type, String period) {
        return getHealthDataTrend(type, period, null);
    }

    @Override
    public List<HealthDataResponse> getHealthDataTrend(String type, String period, Long targetUserId) {
        Long userId = resolveAccessibleUserId(targetUserId);
        int limit = switch (period) {
            case "month" -> 30;
            case "year" -> 365;
            default -> 7;
        };

        List<HealthData> healthDataList = healthDataRepository.findByUserIdAndTypeOrderByRecordDateDesc(
                userId,
                type,
                PageRequest.of(0, limit)
        );
        Collections.reverse(healthDataList);
        return healthDataList.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    @Override
    public HealthDataImportResult importHealthData(List<HealthDataRequest> requests) {
        return importHealthData(requests, null);
    }

    @Override
    public HealthDataImportResult importHealthData(List<HealthDataRequest> requests, Long targetUserId) {
        HealthDataImportResult result = new HealthDataImportResult();
        if (requests == null || requests.isEmpty()) {
            result.getErrors().add("没有可导入的数据");
            result.setFailureCount(1);
            return result;
        }

        Long userId = resolveAccessibleUserId(targetUserId);
        for (int i = 0; i < requests.size(); i++) {
            HealthDataRequest request = requests.get(i);
            try {
                HealthData saved = saveHealthData(null, userId, request);
                healthAlertService.handleAbnormalMetric(saved);
                result.getImportedRecords().add(convertToResponse(saved));
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception e) {
                result.setFailureCount(result.getFailureCount() + 1);
                result.getErrors().add("第 " + (i + 1) + " 行导入失败: " + e.getMessage());
            }
        }

        clearHealthDataCache(userId, null);
        return result;
    }

    @Override
    public String exportHealthData(String type, String startDate, String endDate) {
        return exportHealthData(type, startDate, endDate, null);
    }

    @Override
    public String exportHealthData(String type, String startDate, String endDate, Long targetUserId) {
        List<HealthDataResponse> dataList = getHealthDataList(type, startDate, endDate, targetUserId);
        StringJoiner joiner = new StringJoiner(System.lineSeparator());
        joiner.add("type,value,unit,recordDate,notes");
        for (HealthDataResponse item : dataList) {
            joiner.add(String.join(",",
                    escapeCsv(item.getType()),
                    String.valueOf(item.getValue()),
                    escapeCsv(item.getUnit()),
                    escapeCsv(item.getRecordDate()),
                    escapeCsv(item.getNotes())
            ));
        }
        return joiner.toString();
    }

    @Override
    public String getImportTemplate() {
        return String.join(System.lineSeparator(),
                "type,value,unit,recordDate,notes",
                "steps,8500,步,2026-06-10,晚饭后散步",
                "sleep,7.5,小时,2026-06-10,睡眠较稳定");
    }

    @Override
    public void deleteHealthData(Long id) {
        deleteHealthData(id, null);
    }

    @Override
    public void deleteHealthData(Long id, Long targetUserId) {
        HealthData healthData = healthDataRepository.findById(id).orElseThrow(() -> new RuntimeException("健康数据不存在"));
        Long userId = resolveAccessibleUserId(targetUserId);
        if (!healthData.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除此数据");
        }
        healthDataRepository.delete(healthData);
        clearHealthDataCache(userId, healthData.getType());
    }

    private HealthData saveHealthData(Long id, Long userId, HealthDataRequest request) {
        validateRequest(request);
        HealthData healthData;
        if (id == null) {
            healthData = new HealthData();
            healthData.setUserId(userId);
        } else {
            healthData = healthDataRepository.findById(id).orElseThrow(() -> new RuntimeException("健康数据不存在"));
            if (!healthData.getUserId().equals(userId)) {
                throw new RuntimeException("无权修改此数据");
            }
        }

        healthData.setType(normalizeType(request.getType()));
        healthData.setDataValue(request.getValue());
        healthData.setUnit(StringUtils.hasText(request.getUnit()) ? request.getUnit().trim() : null);
        healthData.setNotes(StringUtils.hasText(request.getNotes()) ? request.getNotes().trim() : null);
        healthData.setRecordDate(parseRecordDate(request.getRecordDate()));
        return healthDataRepository.save(healthData);
    }

    private void validateRequest(HealthDataRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (!StringUtils.hasText(request.getType())) {
            throw new IllegalArgumentException("数据类型不能为空");
        }
        if (request.getValue() == null) {
            throw new IllegalArgumentException("数据值不能为空");
        }
    }

    private List<HealthData> queryHealthData(Long userId, String type, LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null) {
            if (type != null) {
                return healthDataRepository.findByUserIdAndTypeAndRecordDateBetween(userId, type, start, end);
            }
            return healthDataRepository.findByUserIdAndRecordDateBetween(userId, start, end);
        }
        if (type != null) {
            return healthDataRepository.findByUserIdAndTypeOrderByRecordDateDesc(userId, type);
        }
        return healthDataRepository.findByUserIdOrderByRecordDateDesc(userId);
    }

    private void clearHealthDataCache(Long userId, String type) {
        deleteCacheQuietly(buildCacheKey(userId, normalizeType(type), null, null));
        deleteCacheQuietly(buildCacheKey(userId, null, null, null));
    }

    private void deleteCacheQuietly(String cacheKey) {
        try {
            redisUtils.delete(cacheKey);
        } catch (Exception e) {
            log.info("清理缓存失败: {}", e.getMessage());
        }
    }

    private String buildCacheKey(Long userId, String type, String startDate, String endDate) {
        return HEALTH_DATA_CACHE_PREFIX + userId + ":" + (type != null ? type : "all") + ":"
                + (StringUtils.hasText(startDate) ? startDate : "all") + ":"
                + (StringUtils.hasText(endDate) ? endDate : "all");
    }

    private HealthDataResponse convertToResponse(HealthData healthData) {
        HealthDataResponse response = new HealthDataResponse();
        BeanUtils.copyProperties(healthData, response);
        response.setValue(healthData.getDataValue());
        response.setNotes(healthData.getNotes());
        response.setRecordDate(formatDateTime(healthData.getRecordDate()));
        response.setCreatedAt(formatDateTime(healthData.getCreatedAt()));
        response.setUpdatedAt(formatDateTime(healthData.getUpdatedAt()));
        return response;
    }

    private String normalizeType(String type) {
        return StringUtils.hasText(type) ? type.trim() : null;
    }

    private LocalDateTime parseRecordDate(String value) {
        if (!StringUtils.hasText(value)) {
            return LocalDateTime.now();
        }
        String trimmed = value.trim();
        try {
            if (trimmed.length() == 10) {
                return LocalDate.parse(trimmed).atStartOfDay();
            }
            return LocalDateTime.parse(trimmed, DATE_TIME_FORMATTER);
        } catch (Exception e) {
            throw new IllegalArgumentException("记录日期格式不正确，应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss");
        }
    }

    private LocalDateTime parseQueryStart(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() == 10) {
            return LocalDate.parse(trimmed).atStartOfDay();
        }
        return LocalDateTime.parse(trimmed, DATE_TIME_FORMATTER);
    }

    private LocalDateTime parseQueryEnd(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() == 10) {
            return LocalDate.parse(trimmed).atTime(LocalTime.MAX.withNano(0));
        }
        return LocalDateTime.parse(trimmed, DATE_TIME_FORMATTER);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME_FORMATTER);
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private Long getCurrentUserId() {
        Long userId = jwtUtils.getCurrentUserId();
        return userId == null ? 1L : userId;
    }

    private Long resolveAccessibleUserId(Long targetUserId) {
        return familyService.resolveAccessibleUserId(targetUserId);
    }
}
