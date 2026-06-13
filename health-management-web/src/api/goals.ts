import request from '../utils/request';

export interface HealthGoalRequest {
  type: string;
  targetValue: number;
  unit?: string;
  period: 'daily' | 'weekly' | 'monthly';
  enabled?: boolean;
}

export interface HealthGoalResponse {
  id: number;
  type: string;
  targetValue: number;
  unit?: string;
  period: string;
  enabled: boolean;
  currentValue: number;
  progress: number;
  remainingValue: number;
  createdAt: string;
  updatedAt?: string;
}

export function getGoals(enabledOnly?: boolean) {
  return request.get<any, { data: HealthGoalResponse[] }>('/goals', {
    params: { enabledOnly }
  });
}

export function getGoalsForUser(enabledOnly?: boolean, targetUserId?: number) {
  return request.get<any, { data: HealthGoalResponse[] }>('/goals', {
    params: { enabledOnly, targetUserId }
  });
}

export function createGoal(data: HealthGoalRequest, targetUserId?: number) {
  return request.post<any, { data: HealthGoalResponse }>('/goals', data, {
    params: { targetUserId }
  });
}

export function updateGoal(id: number, data: HealthGoalRequest, targetUserId?: number) {
  return request.put<any, { data: HealthGoalResponse }>(`/goals/${id}`, data, {
    params: { targetUserId }
  });
}

export function deleteGoal(id: number, targetUserId?: number) {
  return request.delete<any, { data: null }>(`/goals/${id}`, {
    params: { targetUserId }
  });
}
