package com.health.repository;

import com.health.entity.HealthGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HealthGoalRepository extends JpaRepository<HealthGoal, Long> {

    List<HealthGoal> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<HealthGoal> findByUserIdAndEnabledTrueOrderByCreatedAtDesc(Long userId);

    List<HealthGoal> findByUserIdAndTypeAndEnabledTrueOrderByCreatedAtDesc(Long userId, String type);
}
