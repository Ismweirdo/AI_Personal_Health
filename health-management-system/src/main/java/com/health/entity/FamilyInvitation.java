package com.health.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "family_invitations", indexes = {
        @Index(name = "idx_family_invitation_family", columnList = "family_id"),
        @Index(name = "idx_family_invitation_inviter", columnList = "inviter_user_id"),
        @Index(name = "idx_family_invitation_phone", columnList = "invitee_phone"),
        @Index(name = "idx_family_invitation_code", columnList = "invite_code"),
        @Index(name = "idx_family_invitation_status", columnList = "status")
})
public class FamilyInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "inviter_user_id", nullable = false)
    private Long inviterUserId;

    @Column(name = "invitee_phone", nullable = false, length = 20)
    private String inviteePhone;

    @Column(name = "invitee_role", nullable = false, length = 20)
    private String inviteeRole;

    @Column(name = "invite_code", nullable = false, unique = true, length = 20)
    private String inviteCode;

    @Column(nullable = false, length = 20)
    private String status = "pending";

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "accepted_by_user_id")
    private Long acceptedByUserId;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
