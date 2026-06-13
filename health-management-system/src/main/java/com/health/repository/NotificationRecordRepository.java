package com.health.repository;

import com.health.entity.NotificationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRecordRepository extends JpaRepository<NotificationRecord, Long> {

    List<NotificationRecord> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<NotificationRecord> findByActionTypeAndActionRefId(String actionType, Long actionRefId);

    boolean existsByUserIdAndActionTypeAndActionRefId(Long userId, String actionType, Long actionRefId);

    boolean existsByRuleIdAndScheduledFor(Long ruleId, LocalDateTime scheduledFor);

    long countByUserIdAndStatus(Long userId, String status);
}
