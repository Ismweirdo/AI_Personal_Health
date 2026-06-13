import request from '../utils/request';

export interface DeviceRegistrationRequest {
  deviceName: string;
  deviceType: string;
  deviceModel?: string;
  manufacturer?: string;
  firmwareVersion?: string;
  description?: string;
}

export interface DeviceRegistrationResponse {
  deviceId: string;
  apiKey: string;
  deviceName: string;
  deviceType: string;
  deviceModel?: string;
  manufacturer?: string;
  firmwareVersion?: string;
  status: string;
  createdAt: string;
  message: string;
}

export interface DeviceInfoResponse {
  id: number;
  deviceId: string;
  deviceName: string;
  deviceType: string;
  deviceModel?: string;
  manufacturer?: string;
  firmwareVersion?: string;
  status: string;
  lastActive?: string;
  description?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface DeviceDataRequest {
  deviceId: string;
  apiKey: string;
  data: Array<{
    type: string;
    value: number;
    unit?: string;
    recordDate: string;
    notes?: string;
  }>;
}

export interface DeviceDataResponse {
  requestId: string;
  deviceId: string;
  deviceName: string;
  receivedAt: string;
  totalCount: number;
  successCount: number;
  failureCount: number;
  results: Array<{
    type: string;
    value: number;
    unit?: string;
    recordDate: string;
    success: boolean;
    errorMessage?: string;
    healthDataId?: number;
  }>;
  message: string;
}

export interface HuaweiBindRequest {
  phone: string;
  deviceName?: string;
  deviceModel?: string;
  authorizationCode?: string;
}

export interface HuaweiBindResponse {
  deviceId: string;
  status: string;
  message: string;
  authorizationUrl?: string;
  nextAction?: string;
}

export interface HuaweiSyncResponse {
  status: string;
  message: string;
  importedCount: number;
  nextAction?: string;
}

// 注册设备
export function registerDevice(data: DeviceRegistrationRequest) {
  return request.post<any, { data: DeviceRegistrationResponse }>('/device/register', data);
}

// 获取用户设备列表
export function getUserDevices() {
  return request.get<any, { data: DeviceInfoResponse[] }>('/device/list');
}

// 获取设备详细信息
export function getDeviceInfo(deviceId: string) {
  return request.get<any, { data: DeviceInfoResponse }>(`/device/info/${deviceId}`);
}

// 删除设备
export function deleteDevice(deviceId: string) {
  return request.delete<any, { data: null }>(`/device/${deviceId}`);
}

// 更新设备状态
export function updateDeviceStatus(deviceId: string, status: string) {
  return request.put<any, { data: null }>(`/device/${deviceId}/status`, null, {
    params: { status }
  });
}

// 验证API密钥
export function validateApiKey(apiKey: string) {
  return request.get<any, { data: boolean }>('/device/validate-key', {
    params: { apiKey }
  });
}

// 设备数据写入（供设备使用）
export function sendDeviceData(data: DeviceDataRequest) {
  return request.post<any, { data: DeviceDataResponse }>('/device/data', data);
}

export function bindHuaweiDevice(data: HuaweiBindRequest) {
  return request.post<any, { data: HuaweiBindResponse }>('/device/huawei/bind', data);
}

export function syncHuaweiDevice(deviceId: string) {
  return request.post<any, { data: HuaweiSyncResponse }>(`/device/huawei/${deviceId}/sync`);
}

// 设备类型选项
export const deviceTypes = [
  { label: '华为运动健康', value: 'huawei_health' },
  { label: '智能手环', value: 'fitness_tracker' },
  { label: '智能手表', value: 'smartwatch' },
  { label: '血压计', value: 'blood_pressure_monitor' },
  { label: '血糖仪', value: 'blood_glucose_meter' },
  { label: '体重秤', value: 'weight_scale' },
  { label: '睡眠监测器', value: 'sleep_monitor' },
  { label: '心率监测器', value: 'heart_rate_monitor' },
  { label: '其他设备', value: 'other' }
];

// 设备状态选项
export const deviceStatuses = [
  { label: '活跃', value: 'active', color: 'green' },
  { label: '未激活', value: 'inactive', color: 'yellow' },
  { label: '已禁用', value: 'disabled', color: 'red' }
];

// 获取设备类型标签
export function getDeviceTypeLabel(type: string): string {
  const item = deviceTypes.find(t => t.value === type);
  return item?.label || type;
}

// 获取设备状态标签
export function getStatusLabel(status: string): string {
  const item = deviceStatuses.find(s => s.value === status);
  return item?.label || status;
}

// 获取设备状态颜色
export function getStatusClass(status: string): string {
  return status || 'inactive';
}
