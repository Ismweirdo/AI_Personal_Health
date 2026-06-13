import request from '../utils/request';

export interface ReminderRuleRequest {
  title: string;
  type?: string;
  message?: string;
  frequency: 'daily' | 'weekly' | 'once';
  remindTime?: string;
  remindDate?: string;
  weeklyDay?: number;
  enabled?: boolean;
}

export interface ReminderRuleResponse {
  id: number;
  title: string;
  type?: string;
  message?: string;
  frequency: string;
  remindTime?: string;
  remindDate?: string;
  weeklyDay?: number;
  enabled: boolean;
  nextTriggerAt?: string;
  lastTriggeredAt?: string;
  createdAt: string;
}

export interface NotificationResponse {
  id: number;
  ruleId?: number;
  title: string;
  type?: string;
  message?: string;
  actionType?: string;
  actionRefId?: number;
  actionStatus?: string;
  status: string;
  scheduledFor?: string;
  readAt?: string;
  createdAt: string;
}

export function getReminderRules() {
  return request.get<any, { data: ReminderRuleResponse[] }>('/reminders/rules');
}

export function getReminderRulesForUser(targetUserId?: number) {
  return request.get<any, { data: ReminderRuleResponse[] }>('/reminders/rules', {
    params: { targetUserId }
  });
}

export function createReminderRule(data: ReminderRuleRequest, targetUserId?: number) {
  return request.post<any, { data: ReminderRuleResponse }>('/reminders/rules', data, {
    params: { targetUserId }
  });
}

export function updateReminderRule(id: number, data: ReminderRuleRequest, targetUserId?: number) {
  return request.put<any, { data: ReminderRuleResponse }>(`/reminders/rules/${id}`, data, {
    params: { targetUserId }
  });
}

export function toggleReminderRuleEnabled(id: number, enabled: boolean, targetUserId?: number) {
  return request.put<any, { data: ReminderRuleResponse }>(`/reminders/rules/${id}/enabled`, { enabled }, {
    params: { targetUserId }
  });
}

export function deleteReminderRule(id: number, targetUserId?: number) {
  return request.delete<any, { data: null }>(`/reminders/rules/${id}`, {
    params: { targetUserId }
  });
}

export function getNotifications() {
  return request.get<any, { data: NotificationResponse[] }>('/reminders/notifications');
}

export function markNotificationRead(id: number) {
  return request.put<any, { data: NotificationResponse }>(`/reminders/notifications/${id}/read`);
}

export function deleteNotification(id: number) {
  return request.delete<any, { data: null }>(`/reminders/notifications/${id}`);
}
