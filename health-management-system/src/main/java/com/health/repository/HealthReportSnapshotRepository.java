package com.health.repository;

import com.health.entity.HealthReportSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HealthReportSnapshotRepository extends JpaRepository<HealthReportSnapshot, Long> {

    Optional<HealthReportSnapshot> findByUserIdAndPeriodAndStartDateAndEndDate(
            Long userId, String period, LocalDate startDate, LocalDate endDate);

    List<HealthReportSnapshot> findByUserIdAndPeriodOrderByStartDateDesc(Long userId, String period);

    Optional<HealthReportSnapshot> findByIdAndUserId(Long id, Long userId);
}
