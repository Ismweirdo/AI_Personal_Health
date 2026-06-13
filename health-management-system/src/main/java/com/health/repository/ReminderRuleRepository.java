package com.health.repository;

import com.health.entity.ReminderRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ReminderRuleRepository extends JpaRepository<ReminderRule, Long> {

    List<ReminderRule> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ReminderRule> findByUserIdAndEnabledTrueAndNextTriggerAtLessThanEqual(Long userId, LocalDateTime currentTime);

    List<ReminderRule> findByEnabledTrueAndNextTriggerAtLessThanEqualOrderByNextTriggerAtAsc(LocalDateTime currentTime);
}
