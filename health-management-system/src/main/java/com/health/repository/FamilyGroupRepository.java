package com.health.repository;

import com.health.entity.FamilyGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FamilyGroupRepository extends JpaRepository<FamilyGroup, Long> {

    List<FamilyGroup> findByCreatorUserIdAndStatusOrderByCreatedAtDesc(Long creatorUserId, String status);
}
