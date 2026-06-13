package com.health.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FamilyGroupResponse {

    private Long id;
    private String name;
    private Long creatorUserId;
    private String creatorUsername;
    private Integer maxMembers;
    private Integer memberCount;
    private String myRole;
    private Boolean creator;
    private String status;
    private String createdAt;
    private List<FamilyMemberResponse> members = new ArrayList<>();
}
