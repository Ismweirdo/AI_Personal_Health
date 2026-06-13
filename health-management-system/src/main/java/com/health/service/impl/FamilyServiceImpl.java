package com.health.service.impl;

import com.health.common.ErrorCode;
import com.health.dto.FamilyChildResponse;
import com.health.dto.FamilyGroupRequest;
import com.health.dto.FamilyGroupResponse;
import com.health.dto.FamilyInvitationAcceptRequest;
import com.health.dto.FamilyInvitationRequest;
import com.health.dto.FamilyInvitationResponse;
import com.health.dto.FamilyMemberResponse;
import com.health.dto.FamilyParentResponse;
import com.health.entity.FamilyGroup;
import com.health.entity.FamilyInvitation;
import com.health.entity.FamilyMember;
import com.health.entity.NotificationRecord;
import com.health.entity.User;
import com.health.exception.BusinessException;
import com.health.repository.FamilyGroupRepository;
import com.health.repository.FamilyInvitationRepository;
import com.health.repository.FamilyMemberRepository;
import com.health.repository.NotificationRecordRepository;
import com.health.repository.UserRepository;
import com.health.service.FamilyService;
import com.health.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FamilyServiceImpl implements FamilyService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String ACTIVE_STATUS = "active";
    private static final String PENDING_STATUS = "pending";
    private static final String APPROVAL_PENDING_STATUS = "approval_pending";
    private static final String ACCEPTED_STATUS = "accepted";
    private static final String CANCELED_STATUS = "canceled";
    private static final String EXPIRED_STATUS = "expired";
    private static final String REJECTED_STATUS = "rejected";
    private static final String APPROVAL_REJECTED_STATUS = "approval_rejected";
    private static final String PARENT_ROLE = "parent";
    private static final String CHILD_ROLE = "child";
    private static final int DEFAULT_MAX_MEMBERS = 5;
    private static final int ABSOLUTE_MAX_MEMBERS = 10;
    private static final String INVITE_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int INVITE_CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private FamilyGroupRepository familyGroupRepository;

    @Autowired
    private FamilyInvitationRepository invitationRepository;

    @Autowired
    private FamilyMemberRepository memberRepository;

    @Autowired
    private NotificationRecordRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    @Transactional
    public FamilyGroupResponse createFamily(FamilyGroupRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new IllegalArgumentException("家庭名称不能为空");
        }

        Long userId = getCurrentUserId();
        userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        assertUserHasNoActiveFamily(userId);

        FamilyGroup family = new FamilyGroup();
        family.setName(request.getName().trim());
        family.setCreatorUserId(userId);
        family.setMaxMembers(DEFAULT_MAX_MEMBERS);
        family.setStatus(ACTIVE_STATUS);
        family = familyGroupRepository.save(family);

        FamilyMember creatorMember = new FamilyMember();
        creatorMember.setFamilyId(family.getId());
        creatorMember.setUserId(userId);
        creatorMember.setRole(PARENT_ROLE);
        creatorMember.setStatus(ACTIVE_STATUS);
        memberRepository.save(creatorMember);

        return toGroupResponse(family, List.of(creatorMember));
    }

    @Override
    public List<FamilyGroupResponse> getMyFamilies() {
        Long userId = getCurrentUserId();
        List<FamilyMember> memberships = memberRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, ACTIVE_STATUS);
        Map<Long, FamilyMember> membershipMap = memberships.stream()
                .collect(Collectors.toMap(FamilyMember::getFamilyId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        List<FamilyGroup> createdFamilies = familyGroupRepository.findByCreatorUserIdAndStatusOrderByCreatedAtDesc(userId, ACTIVE_STATUS);
        for (FamilyGroup family : createdFamilies) {
            if (!membershipMap.containsKey(family.getId())) {
                membershipMap.put(family.getId(), ensureCreatorMembership(family, userId));
            }
        }

        if (membershipMap.isEmpty()) {
            return List.of();
        }

        Map<Long, FamilyGroup> families = loadFamilies(new ArrayList<>(membershipMap.keySet()));
        return membershipMap.entrySet().stream()
                .map(entry -> toGroupResponse(families.get(entry.getKey()), List.of(entry.getValue())))
                .filter(response -> response.getId() != null)
                .collect(Collectors.toList());
    }

    @Override
    public FamilyGroupResponse getFamily(Long familyId) {
        FamilyGroup family = getActiveFamily(familyId);
        assertActiveMember(familyId, getCurrentUserId());
        return toGroupResponse(family, memberRepository.findByFamilyIdAndStatusOrderByCreatedAtAsc(familyId, ACTIVE_STATUS));
    }

    @Override
    @Transactional
    public void dissolveFamily(Long familyId) {
        FamilyGroup family = getActiveFamily(familyId);
        assertCreator(family, getCurrentUserId());
        family.setStatus("dissolved");
        familyGroupRepository.save(family);

        List<FamilyMember> members = memberRepository.findByFamilyIdAndStatusOrderByCreatedAtAsc(familyId, ACTIVE_STATUS);
        for (FamilyMember member : members) {
            member.setStatus("removed");
            memberRepository.save(member);
        }

        List<FamilyInvitation> invitations = invitationRepository.findByFamilyIdOrderByCreatedAtDesc(familyId);
        for (FamilyInvitation invitation : invitations) {
            if (PENDING_STATUS.equals(invitation.getStatus()) || APPROVAL_PENDING_STATUS.equals(invitation.getStatus())) {
                invitation.setStatus(CANCELED_STATUS);
                invitationRepository.save(invitation);
                completeInvitationNotifications(invitation.getId(), CANCELED_STATUS);
            }
        }
    }

    @Override
    @Transactional
    public FamilyGroupResponse transferCreator(Long familyId, Long newCreatorUserId) {
        FamilyGroup family = getActiveFamily(familyId);
        Long currentUserId = getCurrentUserId();
        assertCreator(family, currentUserId);
        if (family.getCreatorUserId().equals(newCreatorUserId)) {
            return getFamily(familyId);
        }

        FamilyMember newCreator = memberRepository.findByFamilyIdAndUserId(familyId, newCreatorUserId)
                .orElseThrow(() -> new IllegalArgumentException("新创建者必须是家庭成员"));
        if (!ACTIVE_STATUS.equals(newCreator.getStatus()) || !PARENT_ROLE.equals(newCreator.getRole())) {
            throw new IllegalArgumentException("只能将创建者身份转让给家庭中的家长");
        }

        FamilyMember currentCreator = ensureCreatorMembership(family, currentUserId);
        currentCreator.setRole(PARENT_ROLE);
        currentCreator.setStatus(ACTIVE_STATUS);
        memberRepository.save(currentCreator);

        family.setCreatorUserId(newCreatorUserId);
        family = familyGroupRepository.save(family);
        return toGroupResponse(family, memberRepository.findByFamilyIdAndStatusOrderByCreatedAtAsc(familyId, ACTIVE_STATUS));
    }

    @Override
    @Transactional
    public FamilyGroupResponse expandFamily(Long familyId) {
        FamilyGroup family = getActiveFamily(familyId);
        assertCreator(family, getCurrentUserId());
        if (family.getMaxMembers() == null || family.getMaxMembers() < DEFAULT_MAX_MEMBERS) {
            family.setMaxMembers(DEFAULT_MAX_MEMBERS);
        }
        if (family.getMaxMembers() >= ABSOLUTE_MAX_MEMBERS) {
            throw new IllegalArgumentException("家庭人数上限已为10人，不能继续扩容");
        }
        long memberCount = memberRepository.countByFamilyIdAndStatus(family.getId(), ACTIVE_STATUS);
        if (memberCount < family.getMaxMembers()) {
            throw new IllegalArgumentException("家庭人数达到5人后才能扩容");
        }
        family.setMaxMembers(ABSOLUTE_MAX_MEMBERS);
        family = familyGroupRepository.save(family);
        return toGroupResponse(family, memberRepository.findByFamilyIdAndStatusOrderByCreatedAtAsc(familyId, ACTIVE_STATUS));
    }

    @Override
    @Transactional
    public FamilyInvitationResponse createInvitation(Long familyId, FamilyInvitationRequest request) {
        if (request == null || !StringUtils.hasText(request.getInviteePhone())) {
            throw new IllegalArgumentException("被邀请人手机号不能为空");
        }
        FamilyGroup family = getActiveFamily(familyId);
        FamilyMember inviterMembership = assertParentMember(familyId, getCurrentUserId());
        assertFamilyHasCapacity(family);

        String inviteePhone = normalizePhone(request.getInviteePhone());
        String inviteeRole = normalizeRole(request.getInviteeRole());
        User inviter = getCurrentUser();
        if (StringUtils.hasText(inviter.getPhone()) && inviteePhone.equals(normalizePhone(inviter.getPhone()))) {
            throw new IllegalArgumentException("不能邀请自己的手机号");
        }
        User invitee = userRepository.findByPhone(inviteePhone)
                .orElseThrow(() -> new IllegalArgumentException("被邀请手机号未注册，无法发送站内通知"));
        assertUserHasNoActiveFamily(invitee.getId());
        if (memberRepository.existsByFamilyIdAndUserIdAndStatus(familyId, invitee.getId(), ACTIVE_STATUS)) {
            throw new IllegalArgumentException("该用户已在家庭组中");
        }

        int expiresInDays = request.getExpiresInDays() != null ? request.getExpiresInDays() : 7;
        FamilyInvitation invitation = new FamilyInvitation();
        invitation.setFamilyId(familyId);
        invitation.setInviterUserId(inviter.getId());
        invitation.setInviteePhone(inviteePhone);
        invitation.setInviteeRole(inviteeRole);
        invitation.setInviteCode(generateUniqueInviteCode());
        boolean inviterIsCreator = family.getCreatorUserId().equals(inviter.getId());
        invitation.setStatus(inviterIsCreator ? PENDING_STATUS : APPROVAL_PENDING_STATUS);
        invitation.setExpiresAt(LocalDateTime.now().plusDays(expiresInDays));
        invitation = invitationRepository.save(invitation);

        if (inviterIsCreator) {
            notifyInvitee(invitation, family, inviter, invitee);
        } else {
            notifyCreatorForApproval(invitation, family, inviter, inviterMembership);
        }
        return toInvitationResponse(invitation, family);
    }

    @Override
    public List<FamilyInvitationResponse> getFamilyInvitations(Long familyId) {
        FamilyGroup family = getActiveFamily(familyId);
        assertParentMember(familyId, getCurrentUserId());
        return invitationRepository.findByFamilyIdOrderByCreatedAtDesc(familyId)
                .stream()
                .map(invitation -> toInvitationResponse(invitation, family))
                .collect(Collectors.toList());
    }

    @Override
    public List<FamilyInvitationResponse> getReceivedInvitations() {
        User currentUser = getCurrentUser();
        if (!StringUtils.hasText(currentUser.getPhone())) {
            return List.of();
        }
        String currentPhone = normalizePhone(currentUser.getPhone());
        List<FamilyInvitation> receivedInvitations = invitationRepository
                .findByInviteePhoneOrderByCreatedAtDesc(currentPhone);
        Map<Long, FamilyGroup> families = loadFamilies(receivedInvitations
                .stream()
                .map(FamilyInvitation::getFamilyId)
                .distinct()
                .toList());
        return receivedInvitations.stream()
                .peek(invitation -> ensureInviteeNotification(invitation, families.get(invitation.getFamilyId()), currentUser))
                .map(invitation -> toInvitationResponse(invitation, families.get(invitation.getFamilyId())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public FamilyInvitationResponse acceptInvitation(FamilyInvitationAcceptRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("邀请信息不能为空");
        }
        if (request.getInvitationId() != null) {
            return acceptInvitation(request.getInvitationId());
        }
        if (!StringUtils.hasText(request.getInviteCode())) {
            throw new IllegalArgumentException("邀请码不能为空");
        }
        FamilyInvitation invitation = invitationRepository.findByInviteCode(request.getInviteCode().trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("邀请码不存在"));
        return acceptInvitationInternal(invitation);
    }

    @Override
    @Transactional
    public FamilyInvitationResponse acceptInvitation(Long invitationId) {
        FamilyInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("邀请不存在"));
        return acceptInvitationInternal(invitation);
    }

    @Override
    @Transactional
    public FamilyInvitationResponse rejectInvitation(Long invitationId) {
        FamilyInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("邀请不存在"));
        FamilyGroup family = getActiveFamily(invitation.getFamilyId());
        User currentUser = getCurrentUser();
        if (!PENDING_STATUS.equals(invitation.getStatus())) {
            throw new IllegalArgumentException("该邀请当前不可拒绝");
        }
        if (!StringUtils.hasText(currentUser.getPhone())
                || !normalizePhone(currentUser.getPhone()).equals(normalizePhone(invitation.getInviteePhone()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该邀请仅限指定手机号用户处理");
        }
        invitation.setStatus(REJECTED_STATUS);
        invitation.setRejectedAt(LocalDateTime.now());
        invitation = invitationRepository.save(invitation);
        completeNotificationAction("family_invitation", invitation.getId(), REJECTED_STATUS);
        return toInvitationResponse(invitation, family);
    }

    @Override
    @Transactional
    public FamilyInvitationResponse approveInvitation(Long invitationId) {
        FamilyInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("邀请不存在"));
        FamilyGroup family = getActiveFamily(invitation.getFamilyId());
        Long currentUserId = getCurrentUserId();
        assertCreator(family, currentUserId);
        if (!APPROVAL_PENDING_STATUS.equals(invitation.getStatus())) {
            throw new IllegalArgumentException("该邀请申请当前不可审批");
        }
        assertFamilyHasCapacity(family);
        User invitee = userRepository.findByPhone(invitation.getInviteePhone())
                .orElseThrow(() -> new IllegalArgumentException("被邀请手机号未注册，无法发送站内通知"));
        assertUserHasNoActiveFamily(invitee.getId());
        invitation.setStatus(PENDING_STATUS);
        invitation.setApprovedByUserId(currentUserId);
        invitation.setApprovedAt(LocalDateTime.now());
        invitation = invitationRepository.save(invitation);
        completeNotificationAction("family_invitation_approval", invitation.getId(), "approved");
        notifyInvitee(invitation, family, userRepository.findById(invitation.getInviterUserId()).orElse(null), invitee);
        return toInvitationResponse(invitation, family);
    }

    @Override
    @Transactional
    public FamilyInvitationResponse rejectInvitationApproval(Long invitationId) {
        FamilyInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("邀请不存在"));
        FamilyGroup family = getActiveFamily(invitation.getFamilyId());
        assertCreator(family, getCurrentUserId());
        if (!APPROVAL_PENDING_STATUS.equals(invitation.getStatus())) {
            throw new IllegalArgumentException("该邀请申请当前不可拒绝");
        }
        invitation.setStatus(APPROVAL_REJECTED_STATUS);
        invitation.setRejectedAt(LocalDateTime.now());
        invitation = invitationRepository.save(invitation);
        completeNotificationAction("family_invitation_approval", invitation.getId(), APPROVAL_REJECTED_STATUS);
        return toInvitationResponse(invitation, family);
    }

    @Override
    @Transactional
    public void cancelInvitation(Long invitationId) {
        FamilyInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("邀请不存在"));
        assertParentMember(invitation.getFamilyId(), getCurrentUserId());
        if (!PENDING_STATUS.equals(invitation.getStatus()) && !APPROVAL_PENDING_STATUS.equals(invitation.getStatus())) {
            throw new IllegalArgumentException("只能取消待处理的邀请");
        }
        invitation.setStatus(CANCELED_STATUS);
        invitation = invitationRepository.save(invitation);
        completeInvitationNotifications(invitation.getId(), CANCELED_STATUS);
    }

    @Override
    public List<FamilyMemberResponse> getMembers(Long familyId) {
        FamilyGroup family = getActiveFamily(familyId);
        assertActiveMember(familyId, getCurrentUserId());
        return toMemberResponses(family, memberRepository.findByFamilyIdAndStatusOrderByCreatedAtAsc(familyId, ACTIVE_STATUS));
    }

    @Override
    @Transactional
    public FamilyMemberResponse updateMemberRole(Long familyId, Long memberUserId, String role) {
        FamilyGroup family = getActiveFamily(familyId);
        assertCreator(family, getCurrentUserId());
        String normalizedRole = normalizeRole(role);
        if (family.getCreatorUserId().equals(memberUserId) && !PARENT_ROLE.equals(normalizedRole)) {
            throw new IllegalArgumentException("家庭创建者必须保持家长身份");
        }

        FamilyMember member = memberRepository.findByFamilyIdAndUserId(familyId, memberUserId)
                .orElseThrow(() -> new IllegalArgumentException("家庭成员不存在"));
        if (!ACTIVE_STATUS.equals(member.getStatus())) {
            throw new IllegalArgumentException("家庭成员已不在该家庭中");
        }
        member.setRole(normalizedRole);
        member = memberRepository.save(member);
        return toMemberResponse(family, member, userRepository.findById(memberUserId).orElse(null));
    }

    @Override
    @Transactional
    public void removeMember(Long familyId, Long memberUserId) {
        FamilyGroup family = getActiveFamily(familyId);
        assertCreator(family, getCurrentUserId());
        if (family.getCreatorUserId().equals(memberUserId)) {
            throw new IllegalArgumentException("不能移除家庭创建者");
        }
        FamilyMember member = memberRepository.findByFamilyIdAndUserId(familyId, memberUserId)
                .orElseThrow(() -> new IllegalArgumentException("家庭成员不存在"));
        member.setStatus("removed");
        memberRepository.save(member);
    }

    @Override
    public List<FamilyChildResponse> getChildren() {
        Long currentUserId = getCurrentUserId();
        List<FamilyMember> parentMemberships = findActiveParentMemberships(currentUserId);
        if (parentMemberships.isEmpty()) {
            return List.of();
        }

        Map<Long, FamilyGroup> families = loadFamilies(parentMemberships.stream().map(FamilyMember::getFamilyId).toList());
        List<FamilyChildResponse> responses = new ArrayList<>();
        for (FamilyMember parentMembership : parentMemberships) {
            FamilyGroup family = families.get(parentMembership.getFamilyId());
            List<FamilyMember> childMembers = memberRepository.findByFamilyIdAndRoleAndStatusOrderByCreatedAtAsc(
                    parentMembership.getFamilyId(), CHILD_ROLE, ACTIVE_STATUS);
            Map<Long, User> users = loadUsers(childMembers.stream().map(FamilyMember::getUserId).toList());
            for (FamilyMember childMember : childMembers) {
                if (!childMember.getUserId().equals(currentUserId)) {
                    responses.add(toChildResponse(family, childMember, users.get(childMember.getUserId())));
                }
            }
        }
        return responses;
    }

    @Override
    public List<FamilyParentResponse> getParents() {
        Long currentUserId = getCurrentUserId();
        List<FamilyMember> childMemberships = memberRepository.findByUserIdAndStatusOrderByCreatedAtDesc(currentUserId, ACTIVE_STATUS)
                .stream()
                .filter(member -> CHILD_ROLE.equals(member.getRole()))
                .toList();
        if (childMemberships.isEmpty()) {
            return List.of();
        }

        Map<Long, FamilyGroup> families = loadFamilies(childMemberships.stream().map(FamilyMember::getFamilyId).toList());
        List<FamilyParentResponse> responses = new ArrayList<>();
        for (FamilyMember childMembership : childMemberships) {
            FamilyGroup family = families.get(childMembership.getFamilyId());
            List<FamilyMember> parentMembers = memberRepository.findByFamilyIdAndRoleAndStatusOrderByCreatedAtAsc(
                    childMembership.getFamilyId(), PARENT_ROLE, ACTIVE_STATUS);
            Map<Long, User> users = loadUsers(parentMembers.stream().map(FamilyMember::getUserId).toList());
            for (FamilyMember parentMember : parentMembers) {
                responses.add(toParentResponse(family, parentMember, users.get(parentMember.getUserId())));
            }
        }
        return responses;
    }

    @Override
    public Long resolveAccessibleUserId(Long targetUserId) {
        Long authenticatedUserId = jwtUtils.getCurrentUserId();
        if (targetUserId != null && authenticatedUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录后访问家庭健康数据");
        }
        Long currentUserId = authenticatedUserId == null ? 1L : authenticatedUserId;
        if (targetUserId == null || targetUserId.equals(currentUserId)) {
            return currentUserId;
        }
        assertParentCanAccessChild(currentUserId, targetUserId);
        return targetUserId;
    }

    @Override
    public void assertCanAccessUser(Long targetUserId) {
        resolveAccessibleUserId(targetUserId);
    }

    private FamilyInvitationResponse acceptInvitationInternal(FamilyInvitation invitation) {
        Long currentUserId = getCurrentUserId();
        User currentUser = getCurrentUser();
        FamilyGroup family = getActiveFamily(invitation.getFamilyId());

        if (!PENDING_STATUS.equals(invitation.getStatus())) {
            throw new IllegalArgumentException("该邀请已失效");
        }
        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            invitation.setStatus(EXPIRED_STATUS);
            invitationRepository.save(invitation);
            completeNotificationAction("family_invitation", invitation.getId(), EXPIRED_STATUS);
            throw new IllegalArgumentException("该邀请已过期");
        }
        if (invitation.getInviterUserId().equals(currentUserId)) {
            throw new IllegalArgumentException("不能接受自己创建的家庭邀请");
        }
        if (!StringUtils.hasText(currentUser.getPhone())
                || !normalizePhone(currentUser.getPhone()).equals(normalizePhone(invitation.getInviteePhone()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该邀请仅限指定手机号用户接受");
        }
        if (memberRepository.existsByFamilyIdAndUserIdAndStatus(family.getId(), currentUserId, ACTIVE_STATUS)) {
            throw new IllegalArgumentException("你已在该家庭组中");
        }
        assertUserHasNoActiveFamily(currentUserId);
        assertFamilyHasCapacity(family);

        FamilyMember member = memberRepository.findByFamilyIdAndUserId(family.getId(), currentUserId)
                .orElseGet(FamilyMember::new);
        member.setFamilyId(family.getId());
        member.setUserId(currentUserId);
        member.setRole(invitation.getInviteeRole());
        member.setStatus(ACTIVE_STATUS);
        memberRepository.save(member);

        invitation.setStatus(ACCEPTED_STATUS);
        invitation.setAcceptedByUserId(currentUserId);
        invitation.setAcceptedAt(LocalDateTime.now());
        invitation = invitationRepository.save(invitation);
        completeNotificationAction("family_invitation", invitation.getId(), ACCEPTED_STATUS);
        return toInvitationResponse(invitation, family);
    }

    private void assertParentCanAccessChild(Long parentUserId, Long childUserId) {
        List<FamilyMember> parentMemberships = findActiveParentMemberships(parentUserId);
        for (FamilyMember parentMembership : parentMemberships) {
            if (memberRepository.existsByFamilyIdAndUserIdAndRoleAndStatus(
                    parentMembership.getFamilyId(), childUserId, CHILD_ROLE, ACTIVE_STATUS)) {
                return;
            }
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该儿童的家庭健康数据");
    }

    private List<FamilyMember> findActiveParentMemberships(Long userId) {
        Map<Long, FamilyMember> membershipMap = memberRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, ACTIVE_STATUS)
                .stream()
                .filter(member -> PARENT_ROLE.equals(member.getRole()))
                .collect(Collectors.toMap(FamilyMember::getFamilyId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        List<FamilyGroup> createdFamilies = familyGroupRepository.findByCreatorUserIdAndStatusOrderByCreatedAtDesc(userId, ACTIVE_STATUS);
        for (FamilyGroup family : createdFamilies) {
            if (!membershipMap.containsKey(family.getId())) {
                membershipMap.put(family.getId(), ensureCreatorMembership(family, userId));
            }
        }
        return new ArrayList<>(membershipMap.values());
    }

    private FamilyGroup getActiveFamily(Long familyId) {
        FamilyGroup family = familyGroupRepository.findById(familyId)
                .orElseThrow(() -> new IllegalArgumentException("家庭组不存在"));
        if (!ACTIVE_STATUS.equals(family.getStatus())) {
            throw new IllegalArgumentException("家庭组已不可用");
        }
        return family;
    }

    private FamilyMember assertActiveMember(Long familyId, Long userId) {
        FamilyGroup family = getActiveFamily(familyId);
        FamilyMember member = memberRepository.findByFamilyIdAndUserId(familyId, userId).orElse(null);

        if (member == null) {
            if (family.getCreatorUserId().equals(userId)) {
                return ensureCreatorMembership(family, userId);
            }
            throw new BusinessException(ErrorCode.FORBIDDEN, "你不在该家庭组中");
        }

        if (family.getCreatorUserId().equals(userId)
                && (!ACTIVE_STATUS.equals(member.getStatus()) || !PARENT_ROLE.equals(member.getRole()))) {
            member.setStatus(ACTIVE_STATUS);
            member.setRole(PARENT_ROLE);
            return memberRepository.save(member);
        }

        if (!ACTIVE_STATUS.equals(member.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "你不在该家庭组中");
        }
        return member;
    }

    private FamilyMember ensureCreatorMembership(FamilyGroup family, Long userId) {
        FamilyMember member = memberRepository.findByFamilyIdAndUserId(family.getId(), userId).orElseGet(FamilyMember::new);
        member.setFamilyId(family.getId());
        member.setUserId(userId);
        member.setRole(PARENT_ROLE);
        member.setStatus(ACTIVE_STATUS);
        return memberRepository.save(member);
    }

    private FamilyMember assertParentMember(Long familyId, Long userId) {
        FamilyMember member = assertActiveMember(familyId, userId);
        if (!PARENT_ROLE.equals(member.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有家庭组中的家长可以执行该操作");
        }
        return member;
    }

    private void assertCreator(FamilyGroup family, Long userId) {
        if (!family.getCreatorUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有家庭创建者可以执行该操作");
        }
    }

    private FamilyGroupResponse toGroupResponse(FamilyGroup family, List<FamilyMember> members) {
        FamilyGroupResponse response = new FamilyGroupResponse();
        if (family == null) {
            return response;
        }
        Long currentUserId = getCurrentUserId();
        Map<Long, User> users = loadUsers(members.stream().map(FamilyMember::getUserId).toList());
        response.setId(family.getId());
        response.setName(family.getName());
        response.setCreatorUserId(family.getCreatorUserId());
        response.setCreatorUsername(usernameOf(userRepository.findById(family.getCreatorUserId()).orElse(null)));
        response.setMaxMembers(family.getMaxMembers() == null ? DEFAULT_MAX_MEMBERS : family.getMaxMembers());
        response.setMemberCount((int) members.stream().filter(member -> ACTIVE_STATUS.equals(member.getStatus())).count());
        response.setCreator(family.getCreatorUserId().equals(currentUserId));
        response.setStatus(family.getStatus());
        response.setCreatedAt(formatDateTime(family.getCreatedAt()));
        members.stream()
                .filter(member -> member.getUserId().equals(currentUserId))
                .findFirst()
                .ifPresent(member -> response.setMyRole(member.getRole()));
        response.setMembers(toMemberResponses(family, members, users));
        return response;
    }

    private List<FamilyMemberResponse> toMemberResponses(FamilyGroup family, List<FamilyMember> members) {
        Map<Long, User> users = loadUsers(members.stream().map(FamilyMember::getUserId).toList());
        return toMemberResponses(family, members, users);
    }

    private List<FamilyMemberResponse> toMemberResponses(FamilyGroup family, List<FamilyMember> members, Map<Long, User> users) {
        return members.stream()
                .map(member -> toMemberResponse(family, member, users.get(member.getUserId())))
                .collect(Collectors.toList());
    }

    private FamilyMemberResponse toMemberResponse(FamilyGroup family, FamilyMember member, User user) {
        FamilyMemberResponse response = new FamilyMemberResponse();
        response.setMemberId(member.getId());
        response.setFamilyId(member.getFamilyId());
        response.setUserId(member.getUserId());
        response.setUsername(usernameOf(user));
        response.setEmail(user == null ? null : user.getEmail());
        response.setPhone(user == null ? null : user.getPhone());
        response.setRole(member.getRole());
        response.setStatus(member.getStatus());
        response.setCreator(family != null && family.getCreatorUserId().equals(member.getUserId()));
        response.setJoinedAt(formatDateTime(member.getCreatedAt()));
        return response;
    }

    private FamilyInvitationResponse toInvitationResponse(FamilyInvitation invitation, FamilyGroup family) {
        Map<Long, User> users = loadUsers(invitation.getAcceptedByUserId() == null
                ? List.of(invitation.getInviterUserId())
                : List.of(invitation.getInviterUserId(), invitation.getAcceptedByUserId()));
        User invitee = userRepository.findByPhone(invitation.getInviteePhone()).orElse(null);
        FamilyInvitationResponse response = new FamilyInvitationResponse();
        response.setId(invitation.getId());
        response.setFamilyId(invitation.getFamilyId());
        response.setFamilyName(family == null ? null : family.getName());
        response.setInviterUserId(invitation.getInviterUserId());
        response.setInviterUsername(usernameOf(users.get(invitation.getInviterUserId())));
        response.setInviteePhone(invitation.getInviteePhone());
        response.setInviteeUsername(usernameOf(invitee));
        response.setInviteeRole(invitation.getInviteeRole());
        response.setInviteCode(invitation.getInviteCode());
        response.setStatus(resolveInvitationStatus(invitation));
        response.setExpiresAt(formatDateTime(invitation.getExpiresAt()));
        response.setAcceptedByUserId(invitation.getAcceptedByUserId());
        response.setAcceptedByUsername(usernameOf(users.get(invitation.getAcceptedByUserId())));
        response.setAcceptedAt(formatDateTime(invitation.getAcceptedAt()));
        response.setApprovedByUserId(invitation.getApprovedByUserId());
        response.setApprovedAt(formatDateTime(invitation.getApprovedAt()));
        response.setRejectedAt(formatDateTime(invitation.getRejectedAt()));
        response.setCreatedAt(formatDateTime(invitation.getCreatedAt()));
        return response;
    }

    private String resolveInvitationStatus(FamilyInvitation invitation) {
        if (PENDING_STATUS.equals(invitation.getStatus()) && invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            return EXPIRED_STATUS;
        }
        return invitation.getStatus();
    }

    private FamilyChildResponse toChildResponse(FamilyGroup family, FamilyMember member, User user) {
        FamilyChildResponse response = new FamilyChildResponse();
        response.setFamilyId(member.getFamilyId());
        response.setFamilyName(family == null ? null : family.getName());
        response.setUserId(member.getUserId());
        response.setUsername(usernameOf(user));
        response.setEmail(user == null ? null : user.getEmail());
        response.setPhone(user == null ? null : user.getPhone());
        response.setStatus(member.getStatus());
        response.setLinkedAt(formatDateTime(member.getCreatedAt()));
        return response;
    }

    private FamilyParentResponse toParentResponse(FamilyGroup family, FamilyMember member, User user) {
        FamilyParentResponse response = new FamilyParentResponse();
        response.setFamilyId(member.getFamilyId());
        response.setFamilyName(family == null ? null : family.getName());
        response.setUserId(member.getUserId());
        response.setUsername(usernameOf(user));
        response.setEmail(user == null ? null : user.getEmail());
        response.setPhone(user == null ? null : user.getPhone());
        response.setStatus(member.getStatus());
        response.setLinkedAt(formatDateTime(member.getCreatedAt()));
        return response;
    }

    private Map<Long, FamilyGroup> loadFamilies(List<Long> familyIds) {
        if (familyIds == null || familyIds.isEmpty()) {
            return Map.of();
        }
        return familyGroupRepository.findAllById(familyIds)
                .stream()
                .collect(Collectors.toMap(FamilyGroup::getId, Function.identity()));
    }

    private Map<Long, User> loadUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private String generateUniqueInviteCode() {
        String code;
        do {
            StringBuilder builder = new StringBuilder(INVITE_CODE_LENGTH);
            for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
                builder.append(INVITE_CHARS.charAt(RANDOM.nextInt(INVITE_CHARS.length())));
            }
            code = builder.toString();
        } while (invitationRepository.existsByInviteCode(code));
        return code;
    }

    private String normalizePhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            throw new IllegalArgumentException("手机号不能为空");
        }
        return phone.trim();
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            throw new IllegalArgumentException("成员角色不能为空");
        }
        String normalized = role.trim().toLowerCase();
        if (!PARENT_ROLE.equals(normalized) && !CHILD_ROLE.equals(normalized)) {
            throw new IllegalArgumentException("成员角色必须为 parent 或 child");
        }
        return normalized;
    }

    private User getCurrentUser() {
        Long userId = getCurrentUserId();
        return userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private void notifyCreatorForApproval(FamilyInvitation invitation, FamilyGroup family, User inviter, FamilyMember inviterMembership) {
        if (notificationRepository.existsByUserIdAndActionTypeAndActionRefId(
                family.getCreatorUserId(), "family_invitation_approval", invitation.getId())) {
            return;
        }
        NotificationRecord notification = new NotificationRecord();
        notification.setUserId(family.getCreatorUserId());
        notification.setTitle("家庭邀请申请");
        notification.setType("family_invitation_approval");
        notification.setMessage((inviter == null ? "家庭家长" : inviter.getUsername())
                + " 申请邀请手机号 " + invitation.getInviteePhone()
                + " 以" + roleLabel(invitation.getInviteeRole()) + "身份加入「" + family.getName() + "」。");
        notification.setActionType("family_invitation_approval");
        notification.setActionRefId(invitation.getId());
        notification.setActionStatus("pending");
        notificationRepository.save(notification);
    }

    private void assertUserHasNoActiveFamily(Long userId) {
        if (memberRepository.existsByUserIdAndStatus(userId, ACTIVE_STATUS)) {
            throw new IllegalArgumentException("一个用户同时只能加入一个家庭");
        }
        List<FamilyGroup> createdFamilies = familyGroupRepository.findByCreatorUserIdAndStatusOrderByCreatedAtDesc(userId, ACTIVE_STATUS);
        if (!createdFamilies.isEmpty()) {
            createdFamilies.forEach(family -> ensureCreatorMembership(family, userId));
            throw new IllegalArgumentException("一个用户同时只能加入一个家庭");
        }
    }

    private void assertFamilyHasCapacity(FamilyGroup family) {
        int maxMembers = family.getMaxMembers() == null ? DEFAULT_MAX_MEMBERS : family.getMaxMembers();
        long memberCount = memberRepository.countByFamilyIdAndStatus(family.getId(), ACTIVE_STATUS);
        if (memberCount >= maxMembers) {
            throw new IllegalArgumentException("家庭人数已达上限，请由创建者扩容后再邀请");
        }
    }

    private void notifyInvitee(FamilyInvitation invitation, FamilyGroup family, User inviter, User invitee) {
        ensureInviteeNotification(invitation, family, invitee, inviter);
    }

    private void ensureInviteeNotification(FamilyInvitation invitation, FamilyGroup family, User invitee) {
        if (!PENDING_STATUS.equals(invitation.getStatus())) {
            return;
        }
        ensureInviteeNotification(invitation, family, invitee, userRepository.findById(invitation.getInviterUserId()).orElse(null));
    }

    private void ensureInviteeNotification(FamilyInvitation invitation, FamilyGroup family, User invitee, User inviter) {
        if (family == null || invitee == null || !PENDING_STATUS.equals(invitation.getStatus())) {
            return;
        }
        if (notificationRepository.existsByUserIdAndActionTypeAndActionRefId(
                invitee.getId(), "family_invitation", invitation.getId())) {
            return;
        }
        NotificationRecord notification = new NotificationRecord();
        notification.setUserId(invitee.getId());
        notification.setTitle("家庭组邀请");
        notification.setType("family_invitation");
        notification.setMessage((inviter == null ? "家庭成员" : inviter.getUsername())
                + " 邀请你以" + roleLabel(invitation.getInviteeRole()) + "身份加入「" + family.getName() + "」。");
        notification.setActionType("family_invitation");
        notification.setActionRefId(invitation.getId());
        notification.setActionStatus("pending");
        notificationRepository.save(notification);
    }

    private void completeNotificationAction(String actionType, Long actionRefId, String actionStatus) {
        List<NotificationRecord> notifications = notificationRepository.findByActionTypeAndActionRefId(actionType, actionRefId);
        for (NotificationRecord notification : notifications) {
            notification.setActionStatus(actionStatus);
            notification.setStatus("read");
            notification.setReadAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }
    }

    private void completeInvitationNotifications(Long invitationId, String actionStatus) {
        completeNotificationAction("family_invitation", invitationId, actionStatus);
        completeNotificationAction("family_invitation_approval", invitationId, actionStatus);
    }

    private String roleLabel(String role) {
        return PARENT_ROLE.equals(role) ? "家长" : "儿童";
    }

    private String usernameOf(User user) {
        return user == null ? null : user.getUsername();
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME_FORMATTER);
    }

    private Long getCurrentUserId() {
        Long userId = jwtUtils.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录后访问家庭组");
        }
        return userId;
    }
}
