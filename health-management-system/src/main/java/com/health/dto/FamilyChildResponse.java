package com.health.dto;

import lombok.Data;

@Data
public class FamilyChildResponse {

    private Long familyId;
    private String familyName;
    private Long userId;
    private String username;
    private String email;
    private String phone;
    private String status;
    private String linkedAt;
}
