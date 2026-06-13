package com.health.repository;

import com.health.entity.DeviceDataLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DeviceDataLogRepository extends JpaRepository<DeviceDataLog, Long> {

    List<DeviceDataLog> findByDeviceIdOrderByCreatedAtDesc(String deviceId);

    List<DeviceDataLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<DeviceDataLog> findByDeviceIdAndStatus(String deviceId, String status);

    List<DeviceDataLog> findByUserIdAndCreatedAtBetween(Long userId, LocalDateTime start, LocalDateTime end);

    List<DeviceDataLog> findByRequestId(String requestId);
}