package com.health.repository;

import com.health.entity.HealthDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HealthDeviceRepository extends JpaRepository<HealthDevice, Long> {

    Optional<HealthDevice> findByDeviceId(String deviceId);

    Optional<HealthDevice> findByApiKey(String apiKey);

    List<HealthDevice> findByUserId(Long userId);

    List<HealthDevice> findByUserIdAndStatus(Long userId, String status);

    boolean existsByDeviceId(String deviceId);

    boolean existsByApiKey(String apiKey);
}