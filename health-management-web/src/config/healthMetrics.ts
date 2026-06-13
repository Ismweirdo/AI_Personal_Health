import { Heart, Moon, Footprints, Scale, Gauge, Droplets } from 'lucide-vue-next';
import type { Component } from 'vue';

export interface HealthMetricConfig {
  type: string;
  label: string;
  icon: Component;
  color: string;
  unit: string;
  normalRange: [number, number];
  thresholds: {
    high: number;
    low: number;
  };
}

export const healthMetrics: HealthMetricConfig[] = [
  {
    type: 'steps', label: '步数', icon: Footprints, color: 'text-ink-pine', unit: '步',
    normalRange: [5000, 12000], thresholds: { high: 12000, low: 3000 },
  },
  {
    type: 'heart_rate', label: '心率', icon: Heart, color: 'text-ink-cinnabar', unit: 'bpm',
    normalRange: [60, 100], thresholds: { high: 100, low: 55 },
  },
  {
    type: 'sleep', label: '睡眠', icon: Moon, color: 'text-ink-gold', unit: '小时',
    normalRange: [6, 9], thresholds: { high: 9, low: 5 },
  },
  {
    type: 'weight', label: '体重', icon: Scale, color: 'text-ink-pine-dark', unit: 'kg',
    normalRange: [50, 90], thresholds: { high: 90, low: 50 },
  },
  {
    type: 'blood_pressure', label: '血压', icon: Gauge, color: 'text-ink-gold', unit: 'mmHg',
    normalRange: [90, 140], thresholds: { high: 140, low: 90 },
  },
  {
    type: 'blood_sugar', label: '血糖', icon: Droplets, color: 'text-ink-cinnabar', unit: 'mmol/L',
    normalRange: [3.9, 7.0], thresholds: { high: 7.0, low: 3.5 },
  },
];

export function getMetricConfig(type: string): HealthMetricConfig | undefined {
  return healthMetrics.find(m => m.type === type);
}
