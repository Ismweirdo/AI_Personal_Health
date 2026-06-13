package com.health.repository;

import com.health.entity.FamilyInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FamilyInvitationRepository extends JpaRepository<FamilyInvitation, Long> {

    Optional<FamilyInvitation> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);

    List<FamilyInvitation> findByFamilyIdOrderByCreatedAtDesc(Long familyId);

    List<FamilyInvitation> findByInviterUserIdOrderByCreatedAtDesc(Long inviterUserId);

    List<FamilyInvitation> findByInviteePhoneOrderByCreatedAtDesc(String inviteePhone);
}
