import request from '../utils/request';
import type { HealthGoalResponse } from './goals';

export interface HealthMetricReportItem {
  type: string;
  label: string;
  unit?: string;
  aggregationMode: 'period_total' | 'daily_average';
  recordCount: number;
  activeDays: number;
  summaryValue: number;
  latestValue: number;
  averageValue: number;
  minValue: number;
  maxValue: number;
  trend: string;
  status: string;
}

export interface HealthReportResponse {
  snapshotId?: number;
  period: string;
  startDate: string;
  endDate: string;
  generatedAt: string;
  sourceType: 'live' | 'snapshot';
  summaryMethod: string;
  metrics: HealthMetricReportItem[];
  goals: HealthGoalResponse[];
  highlights: string[];
  suggestions: string[];
}

export interface HealthReportSnapshotSummary {
  id: number;
  period: string;
  startDate: string;
  endDate: string;
  generatedAt: string;
  metricCount: number;
  goalCount: number;
}

export function getHealthReport(period: 'weekly' | 'monthly') {
  return request.get<any, { data: HealthReportResponse }>('/reports', {
    params: { period }
  });
}

export function getHealthReportHistory(period: 'weekly' | 'monthly') {
  return request.get<any, { data: HealthReportSnapshotSummary[] }>('/reports/history', {
    params: { period },
    silentError: true
  } as any);
}

export function getHealthReportSnapshot(snapshotId: number) {
  return request.get<any, { data: HealthReportResponse }>(`/reports/history/${snapshotId}`);
}
