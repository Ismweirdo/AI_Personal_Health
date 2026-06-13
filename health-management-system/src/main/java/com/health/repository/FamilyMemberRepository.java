package com.health.repository;

import com.health.entity.FamilyMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {

    Optional<FamilyMember> findByFamilyIdAndUserId(Long familyId, Long userId);

    boolean existsByFamilyIdAndUserIdAndStatus(Long familyId, Long userId, String status);

    boolean existsByFamilyIdAndUserIdAndRoleAndStatus(Long familyId, Long userId, String role, String status);

    boolean existsByUserIdAndStatus(Long userId, String status);

    long countByFamilyIdAndStatus(Long familyId, String status);

    List<FamilyMember> findByFamilyIdAndStatusOrderByCreatedAtAsc(Long familyId, String status);

    List<FamilyMember> findByFamilyIdAndRoleAndStatusOrderByCreatedAtAsc(Long familyId, String role, String status);

    List<FamilyMember> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
}
