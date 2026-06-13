package com.health.dto;

import lombok.Data;

@Data
public class FamilyMemberResponse {

    private Long memberId;
    private Long familyId;
    private Long userId;
    private String username;
    private String email;
    private String phone;
    private String role;
    private String status;
    private Boolean creator;
    private String joinedAt;
}
