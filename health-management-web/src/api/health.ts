import request from '../utils/request';

export interface HealthDataRequest {
  type: string;
  value: number;
  unit: string;
  recordDate?: string;
  notes?: string;
}

export interface HealthDataResponse {
  id: number;
  userId?: number;
  type: string;
  value: number;
  unit: string;
  recordDate: string;
  notes?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface HealthDataTrend {
  date: string;
  value: number;
}

export interface HealthDataImportResult {
  successCount: number;
  failureCount: number;
  importedRecords: HealthDataResponse[];
  errors: string[];
}

// 添加健康数据
export function addHealthData(data: HealthDataRequest) {
  return request.post<any, { data: HealthDataResponse }>('/health/data', data);
}

export function addHealthDataForUser(data: HealthDataRequest, targetUserId?: number) {
  return request.post<any, { data: HealthDataResponse }>('/health/data', data, {
    params: { targetUserId }
  });
}

export function updateHealthData(id: number, data: HealthDataRequest, targetUserId?: number) {
  return request.put<any, { data: HealthDataResponse }>(`/health/data/${id}`, data, {
    params: { targetUserId }
  });
}

// 获取健康数据列表
export function getHealthDataList(
  type: string,
  startDate?: string,
  endDate?: string,
  targetUserId?: number
) {
  return request.get<any, { data: HealthDataResponse[] }>('/health/data', {
    params: {
      type,
      startDate,
      endDate,
      targetUserId
    }
  });
}

// 获取健康数据趋势
export function getHealthDataTrend(type: string, period: string, targetUserId?: number) {
  return request.get<any, { data: HealthDataResponse[] }>('/health/trend', {
    params: {
      type,
      period,
      targetUserId
    }
  });
}

// 删除健康数据
export function deleteHealthData(id: number, targetUserId?: number) {
  return request.delete<any, { data: null }>(`/health/data/${id}`, {
    params: { targetUserId }
  });
}

export function importHealthData(rows: HealthDataRequest[], targetUserId?: number) {
  return request.post<any, { data: HealthDataImportResult }>('/health/data/import', rows, {
    params: { targetUserId }
  });
}

async function fetchCsv(url: string) {
  const response = await fetch(url, {
    headers: {
      Authorization: `Bearer ${localStorage.getItem('token') || ''}`
    }
  });
  if (!response.ok) {
    throw new Error('下载失败');
  }
  return response.text();
}

export async function exportHealthData(type = '', startDate?: string, endDate?: string, targetUserId?: number) {
  const params = new URLSearchParams();
  if (type) params.set('type', type);
  if (startDate) params.set('startDate', startDate);
  if (endDate) params.set('endDate', endDate);
  if (targetUserId) params.set('targetUserId', String(targetUserId));
  const query = params.toString();
  return fetchCsv(`/api/health/data/export${query ? `?${query}` : ''}`);
}

export async function downloadHealthImportTemplate() {
  return fetchCsv('/api/health/data/import-template');
}

// 获取所有健康数据类型
export const healthDataTypes = [
  { label: '步数', value: 'steps', unit: '步' },
  { label: '心率', value: 'heart_rate', unit: 'bpm' },
  { label: '睡眠', value: 'sleep', unit: '小时' },
  { label: '体重', value: 'weight', unit: 'kg' },
  { label: '血压', value: 'blood_pressure', unit: 'mmHg' },
  { label: '血糖', value: 'blood_sugar', unit: 'mmol/L' }
];

// 获取单位
export function getUnitByType(type: string): string {
  const item = healthDataTypes.find(t => t.value === type);
  return item?.unit || '';
}

// 获取标签
export function getLabelByType(type: string): string {
  const item = healthDataTypes.find(t => t.value === type);
  return item?.label || type;
}
