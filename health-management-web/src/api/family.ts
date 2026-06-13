import request from '../utils/request';

export type FamilyRole = 'parent' | 'child';
export type InvitationStatus = 'pending' | 'approval_pending' | 'accepted' | 'canceled' | 'expired' | 'rejected' | 'approval_rejected';

export interface FamilyGroupRequest {
  name: string;
}

export interface FamilyMemberResponse {
  memberId: number;
  familyId: number;
  userId: number;
  username?: string;
  email?: string;
  phone?: string;
  role: FamilyRole;
  status: string;
  creator: boolean;
  joinedAt?: string;
}

export interface FamilyGroupResponse {
  id: number;
  name: string;
  creatorUserId: number;
  creatorUsername?: string;
  maxMembers?: number;
  memberCount?: number;
  myRole?: FamilyRole;
  creator: boolean;
  status: string;
  createdAt?: string;
  members?: FamilyMemberResponse[];
}

export interface FamilyInvitationRequest {
  inviteePhone: string;
  inviteeRole: FamilyRole;
  expiresInDays?: number;
}

export interface FamilyInvitationResponse {
  id: number;
  familyId: number;
  familyName?: string;
  inviterUserId: number;
  inviterUsername?: string;
  inviteePhone: string;
  inviteeUsername?: string;
  inviteeRole: FamilyRole;
  inviteCode: string;
  status: InvitationStatus;
  expiresAt: string;
  acceptedByUserId?: number;
  acceptedByUsername?: string;
  acceptedAt?: string;
  approvedByUserId?: number;
  approvedAt?: string;
  rejectedAt?: string;
  createdAt?: string;
}

export interface FamilyChildResponse {
  familyId: number;
  familyName?: string;
  userId: number;
  username?: string;
  email?: string;
  phone?: string;
  status: string;
  linkedAt?: string;
}

export interface FamilyParentResponse {
  familyId: number;
  familyName?: string;
  userId: number;
  username?: string;
  email?: string;
  phone?: string;
  status: string;
  linkedAt?: string;
}

export function createFamily(data: FamilyGroupRequest) {
  return request.post<any, { data: FamilyGroupResponse }>('/family/groups', data);
}

export function getMyFamilies() {
  return request.get<any, { data: FamilyGroupResponse[] }>('/family/groups');
}

export function getFamily(familyId: number) {
  return request.get<any, { data: FamilyGroupResponse }>(`/family/groups/${familyId}`);
}

export function dissolveFamily(familyId: number) {
  return request.delete<any, { data: null }>(`/family/groups/${familyId}`);
}

export function transferFamilyCreator(familyId: number, newCreatorUserId: number) {
  return request.put<any, { data: FamilyGroupResponse }>(`/family/groups/${familyId}/creator`, { newCreatorUserId });
}

export function expandFamily(familyId: number) {
  return request.put<any, { data: FamilyGroupResponse }>(`/family/groups/${familyId}/expand`);
}

export function createFamilyInvitation(familyId: number, data: FamilyInvitationRequest) {
  return request.post<any, { data: FamilyInvitationResponse }>(`/family/groups/${familyId}/invitations`, data);
}

export function getFamilyInvitations(familyId: number) {
  return request.get<any, { data: FamilyInvitationResponse[] }>(`/family/groups/${familyId}/invitations`);
}

export function getReceivedInvitations() {
  return request.get<any, { data: FamilyInvitationResponse[] }>('/family/invitations/received');
}

export function acceptInvitation(invitationId: number) {
  return request.post<any, { data: FamilyInvitationResponse }>(`/family/invitations/${invitationId}/accept`);
}

export function rejectInvitation(invitationId: number) {
  return request.post<any, { data: FamilyInvitationResponse }>(`/family/invitations/${invitationId}/reject`);
}

export function approveInvitation(invitationId: number) {
  return request.post<any, { data: FamilyInvitationResponse }>(`/family/invitations/${invitationId}/approve`);
}

export function rejectInvitationApproval(invitationId: number) {
  return request.post<any, { data: FamilyInvitationResponse }>(`/family/invitations/${invitationId}/approval-reject`);
}

export function acceptInvitationByCode(inviteCode: string) {
  return request.post<any, { data: FamilyInvitationResponse }>('/family/invitations/accept', { inviteCode });
}

export function cancelInvitation(invitationId: number) {
  return request.put<any, { data: null }>(`/family/invitations/${invitationId}/cancel`);
}

export function getFamilyMembers(familyId: number) {
  return request.get<any, { data: FamilyMemberResponse[] }>(`/family/groups/${familyId}/members`);
}

export function updateFamilyMemberRole(familyId: number, memberUserId: number, role: FamilyRole) {
  return request.put<any, { data: FamilyMemberResponse }>(`/family/groups/${familyId}/members/${memberUserId}/role`, { role });
}

export function removeFamilyMember(familyId: number, memberUserId: number) {
  return request.delete<any, { data: null }>(`/family/groups/${familyId}/members/${memberUserId}`);
}

export function getFamilyChildren() {
  return request.get<any, { data: FamilyChildResponse[] }>('/family/children');
}

export function getFamilyParents() {
  return request.get<any, { data: FamilyParentResponse[] }>('/family/parents');
}
