package com.health.controller;

import com.health.common.Result;
import com.health.dto.FamilyChildResponse;
import com.health.dto.FamilyGroupRequest;
import com.health.dto.FamilyGroupResponse;
import com.health.dto.FamilyInvitationAcceptRequest;
import com.health.dto.FamilyInvitationRequest;
import com.health.dto.FamilyInvitationResponse;
import com.health.dto.FamilyMemberResponse;
import com.health.dto.FamilyMemberRoleRequest;
import com.health.dto.FamilyParentResponse;
import com.health.dto.FamilyTransferCreatorRequest;
import com.health.service.FamilyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/family")
@Tag(name = "家庭组管理", description = "家庭创建、成员角色、邀请与授权访问接口")
public class FamilyController {

    @Autowired
    private FamilyService familyService;

    @Operation(summary = "创建家庭组")
    @PostMapping("/groups")
    public Result<FamilyGroupResponse> createFamily(@Valid @RequestBody FamilyGroupRequest request) {
        return Result.success(familyService.createFamily(request));
    }

    @Operation(summary = "获取我加入的家庭组")
    @GetMapping("/groups")
    public Result<List<FamilyGroupResponse>> getMyFamilies() {
        return Result.success(familyService.getMyFamilies());
    }

    @Operation(summary = "获取家庭组详情")
    @GetMapping("/groups/{familyId}")
    public Result<FamilyGroupResponse> getFamily(@PathVariable Long familyId) {
        return Result.success(familyService.getFamily(familyId));
    }

    @Operation(summary = "创建者解散家庭组")
    @DeleteMapping("/groups/{familyId}")
    public Result<Void> dissolveFamily(@PathVariable Long familyId) {
        familyService.dissolveFamily(familyId);
        return Result.success();
    }

    @Operation(summary = "创建者转让家庭创建者身份")
    @PutMapping("/groups/{familyId}/creator")
    public Result<FamilyGroupResponse> transferCreator(
            @PathVariable Long familyId,
            @Valid @RequestBody FamilyTransferCreatorRequest request) {
        return Result.success(familyService.transferCreator(familyId, request.getNewCreatorUserId()));
    }

    @Operation(summary = "创建者扩容家庭人数上限")
    @PutMapping("/groups/{familyId}/expand")
    public Result<FamilyGroupResponse> expandFamily(@PathVariable Long familyId) {
        return Result.success(familyService.expandFamily(familyId));
    }

    @Operation(summary = "家庭家长邀请成员")
    @PostMapping("/groups/{familyId}/invitations")
    public Result<FamilyInvitationResponse> createInvitation(
            @PathVariable Long familyId,
            @Valid @RequestBody FamilyInvitationRequest request) {
        return Result.success(familyService.createInvitation(familyId, request));
    }

    @Operation(summary = "获取家庭邀请记录")
    @GetMapping("/groups/{familyId}/invitations")
    public Result<List<FamilyInvitationResponse>> getFamilyInvitations(@PathVariable Long familyId) {
        return Result.success(familyService.getFamilyInvitations(familyId));
    }

    @Operation(summary = "获取发给我的邀请")
    @GetMapping("/invitations/received")
    public Result<List<FamilyInvitationResponse>> getReceivedInvitations() {
        return Result.success(familyService.getReceivedInvitations());
    }

    @Operation(summary = "接受家庭邀请")
    @PostMapping("/invitations/accept")
    public Result<FamilyInvitationResponse> acceptInvitation(@Valid @RequestBody FamilyInvitationAcceptRequest request) {
        return Result.success(familyService.acceptInvitation(request));
    }

    @Operation(summary = "接受家庭邀请")
    @PostMapping("/invitations/{id}/accept")
    public Result<FamilyInvitationResponse> acceptInvitationById(@PathVariable Long id) {
        return Result.success(familyService.acceptInvitation(id));
    }

    @Operation(summary = "拒绝家庭邀请")
    @PostMapping("/invitations/{id}/reject")
    public Result<FamilyInvitationResponse> rejectInvitationById(@PathVariable Long id) {
        return Result.success(familyService.rejectInvitation(id));
    }

    @Operation(summary = "创建者同意家长邀请申请")
    @PostMapping("/invitations/{id}/approve")
    public Result<FamilyInvitationResponse> approveInvitation(@PathVariable Long id) {
        return Result.success(familyService.approveInvitation(id));
    }

    @Operation(summary = "创建者拒绝家长邀请申请")
    @PostMapping("/invitations/{id}/approval-reject")
    public Result<FamilyInvitationResponse> rejectInvitationApproval(@PathVariable Long id) {
        return Result.success(familyService.rejectInvitationApproval(id));
    }

    @Operation(summary = "取消家庭邀请")
    @PutMapping("/invitations/{id}/cancel")
    public Result<Void> cancelInvitation(@PathVariable Long id) {
        familyService.cancelInvitation(id);
        return Result.success();
    }

    @Operation(summary = "获取家庭成员")
    @GetMapping("/groups/{familyId}/members")
    public Result<List<FamilyMemberResponse>> getMembers(@PathVariable Long familyId) {
        return Result.success(familyService.getMembers(familyId));
    }

    @Operation(summary = "创建者调整成员角色")
    @PutMapping("/groups/{familyId}/members/{memberUserId}/role")
    public Result<FamilyMemberResponse> updateMemberRole(
            @PathVariable Long familyId,
            @PathVariable Long memberUserId,
            @Valid @RequestBody FamilyMemberRoleRequest request) {
        return Result.success(familyService.updateMemberRole(familyId, memberUserId, request.getRole()));
    }

    @Operation(summary = "创建者移除家庭成员")
    @DeleteMapping("/groups/{familyId}/members/{memberUserId}")
    public Result<Void> removeMember(@PathVariable Long familyId, @PathVariable Long memberUserId) {
        familyService.removeMember(familyId, memberUserId);
        return Result.success();
    }

    @Operation(summary = "获取我可管理的儿童")
    @GetMapping("/children")
    public Result<List<FamilyChildResponse>> getChildren() {
        return Result.success(familyService.getChildren());
    }

    @Operation(summary = "获取我的家长")
    @GetMapping("/parents")
    public Result<List<FamilyParentResponse>> getParents() {
        return Result.success(familyService.getParents());
    }
}
