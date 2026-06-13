package com.health.service;

import com.health.dto.FamilyChildResponse;
import com.health.dto.FamilyGroupRequest;
import com.health.dto.FamilyGroupResponse;
import com.health.dto.FamilyInvitationAcceptRequest;
import com.health.dto.FamilyInvitationRequest;
import com.health.dto.FamilyInvitationResponse;
import com.health.dto.FamilyMemberResponse;
import com.health.dto.FamilyParentResponse;

import java.util.List;

public interface FamilyService {

    FamilyGroupResponse createFamily(FamilyGroupRequest request);

    List<FamilyGroupResponse> getMyFamilies();

    FamilyGroupResponse getFamily(Long familyId);

    void dissolveFamily(Long familyId);

    FamilyGroupResponse transferCreator(Long familyId, Long newCreatorUserId);

    FamilyGroupResponse expandFamily(Long familyId);

    FamilyInvitationResponse createInvitation(Long familyId, FamilyInvitationRequest request);

    List<FamilyInvitationResponse> getFamilyInvitations(Long familyId);

    List<FamilyInvitationResponse> getReceivedInvitations();

    FamilyInvitationResponse acceptInvitation(FamilyInvitationAcceptRequest request);

    FamilyInvitationResponse acceptInvitation(Long invitationId);

    FamilyInvitationResponse rejectInvitation(Long invitationId);

    FamilyInvitationResponse approveInvitation(Long invitationId);

    FamilyInvitationResponse rejectInvitationApproval(Long invitationId);

    void cancelInvitation(Long invitationId);

    List<FamilyMemberResponse> getMembers(Long familyId);

    FamilyMemberResponse updateMemberRole(Long familyId, Long memberUserId, String role);

    void removeMember(Long familyId, Long memberUserId);

    List<FamilyChildResponse> getChildren();

    List<FamilyParentResponse> getParents();

    Long resolveAccessibleUserId(Long targetUserId);

    void assertCanAccessUser(Long targetUserId);
}
