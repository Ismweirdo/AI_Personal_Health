package com.health.dto;

import lombok.Data;

@Data
public class FamilyInvitationResponse {

    private Long id;
    private Long familyId;
    private String familyName;
    private Long inviterUserId;
    private String inviterUsername;
    private String inviteePhone;
    private String inviteeUsername;
    private String inviteeRole;
    private String inviteCode;
    private String status;
    private String expiresAt;
    private Long acceptedByUserId;
    private String acceptedByUsername;
    private String acceptedAt;
    private Long approvedByUserId;
    private String approvedAt;
    private String rejectedAt;
    private String createdAt;
}
