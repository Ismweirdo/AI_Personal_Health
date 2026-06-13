package com.health.dto;

import lombok.Data;

@Data
public class FamilyInvitationAcceptRequest {

    private Long invitationId;
    private String inviteCode;
}
