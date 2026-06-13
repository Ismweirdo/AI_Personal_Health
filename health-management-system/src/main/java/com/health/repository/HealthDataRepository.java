package com.health.repository;

import com.health.entity.HealthData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface HealthDataRepository extends JpaRepository<HealthData, Long> {

    List<HealthData> findByUserIdAndTypeOrderByRecordDateDesc(Long userId, String type);

    List<HealthData> findByUserIdAndTypeAndRecordDateBetween(
            Long userId, String type, LocalDateTime startDate, LocalDateTime endDate);

    List<HealthData> findByUserIdAndTypeOrderByRecordDateDesc(Long userId, String type, Pageable pageable);
    
    List<HealthData> findByUserIdOrderByRecordDateDesc(Long userId);
    
    List<HealthData> findByUserIdAndRecordDateBetween(
            Long userId, LocalDateTime startDate, LocalDateTime endDate);

    List<HealthData> findByUserIdAndTypeAndRecordDateBetweenOrderByRecordDateAsc(
            Long userId, String type, LocalDateTime startDate, LocalDateTime endDate);

    List<HealthData> findByUserIdAndRecordDateBetweenOrderByRecordDateAsc(
            Long userId, LocalDateTime startDate, LocalDateTime endDate);
}
