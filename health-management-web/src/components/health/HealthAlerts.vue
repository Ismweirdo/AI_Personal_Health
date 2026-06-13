<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { AlertTriangle, X, Bell } from 'lucide-vue-next';
import { getHealthDataList, getLabelByType, getUnitByType, type HealthDataResponse } from '../../api/health';
import { healthMetrics as metricsConfig, getMetricConfig } from '../../config/healthMetrics';

const props = defineProps<{
  refreshTrigger?: number;
}>();

const alerts = ref<Array<{
  id: number;
  type: string;
  severity: 'warning' | 'danger';
  message: string;
  value: number;
  timestamp: string;
  dismissed: boolean;
}>>([]);

const showAlerts = ref(true);

// 健康指标阈值配置（从共享配置生成）
const healthThresholds: Record<string, { min: number; max: number; warningMin: number; warningMax: number }> = {};
metricsConfig.forEach(m => {
  healthThresholds[m.type] = {
    min: m.thresholds.low * 0.5,
    max: m.thresholds.high * 1.5,
    warningMin: m.thresholds.low,
    warningMax: m.thresholds.high,
  };
});

const getIcon = (type: string) => getMetricConfig(type)?.icon || AlertTriangle;

// 检查数据是否异常
const checkHealthData = (data: HealthDataResponse[]) => {
  const newAlerts: typeof alerts.value = [];
  
  // 按类型分组
  const groupedByType = data.reduce((acc, item) => {
    if (!acc[item.type]) acc[item.type] = [];
    acc[item.type].push(item);
    return acc;
  }, {} as Record<string, HealthDataResponse[]>);
  
  // 检查每种类型的最新数据
  Object.entries(groupedByType).forEach(([type, items]) => {
    const thresholds = healthThresholds[type];
    if (!thresholds) return;
    
    // 获取最新记录
    const latest = items.sort((a, b) => 
      new Date(b.recordDate).getTime() - new Date(a.recordDate).getTime()
    )[0];
    
    if (!latest) return;
    
    const value = latest.value;
    let severity: 'warning' | 'danger' | null = null;
    let message = '';
    
    // 危险级别
    if (value < thresholds.min || value > thresholds.max) {
      severity = 'danger';
      message = `${getLabelByType(type)}数值严重异常 (${value} ${getUnitByType(type)})，建议立即就医检查`;
    }
    // 警告级别
    else if (value < thresholds.warningMin) {
      severity = 'warning';
      message = `${getLabelByType(type)}数值偏低 (${value} ${getUnitByType(type)})，建议适当调整`;
    } else if (value > thresholds.warningMax) {
      severity = 'warning';
      message = `${getLabelByType(type)}数值偏高 (${value} ${getUnitByType(type)})，建议适当调整`;
    }
    
    if (severity) {
      newAlerts.push({
        id: latest.id,
        type,
        severity,
        message,
        value,
        timestamp: latest.recordDate,
        dismissed: false
      });
    }
  });
  
  alerts.value = newAlerts;
};

// 加载健康数据并检查
const loadAndCheckData = async () => {
  try {
    const res = await getHealthDataList('', undefined, undefined);
    const data = res.data || [];
    
    // 只检查最近24小时的数据
    const oneDayAgo = new Date();
    oneDayAgo.setDate(oneDayAgo.getDate() - 1);
    
    const recentData = data.filter(item => 
      new Date(item.recordDate) >= oneDayAgo
    );
    
    checkHealthData(recentData);
  } catch (error) {
    console.error('加载健康数据失败:', error);
  }
};

// 关闭提醒
const dismissAlert = (id: number) => {
  const alert = alerts.value.find(a => a.id === id);
  if (alert) {
    alert.dismissed = true;
  }
};

// 关闭所有提醒
const dismissAll = () => {
  showAlerts.value = false;
};

// 未关闭的提醒
const activeAlerts = computed(() => 
  alerts.value.filter(alert => !alert.dismissed)
);

// 监听刷新触发器
watch(() => props.refreshTrigger, loadAndCheckData, { immediate: true });

onMounted(loadAndCheckData);
</script>

<template>
  <div v-if="showAlerts && activeAlerts.length > 0" class="health-alerts space-y-3 mb-6">
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2">
        <Bell class="w-5 h-5 text-yellow-600" />
        <h3 class="text-lg font-semibold text-gray-900">健康提醒</h3>
        <span class="px-2 py-0.5 bg-yellow-100 text-yellow-600 text-xs rounded-full">
          {{ activeAlerts.length }}
        </span>
      </div>
      <button 
        @click="dismissAll"
        class="text-gray-500 hover:text-gray-900 transition-colors"
      >
        <X class="w-5 h-5" />
      </button>
    </div>
    
    <div class="space-y-2">
      <div
        v-for="alert in activeAlerts"
        :key="alert.id"
        class="flex items-start gap-3 p-4 rounded-xl border"
        :class="{
          'bg-red-50 border-red-200': alert.severity === 'danger',
          'bg-yellow-50 border-yellow-200': alert.severity === 'warning'
        }"
      >
        <component 
          :is="getIcon(alert.type)" 
          class="w-5 h-5 mt-0.5 flex-shrink-0"
          :class="alert.severity === 'danger' ? 'text-red-500' : 'text-yellow-600'"
        />
        
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2 mb-1">
            <span 
              class="text-sm font-medium"
              :class="alert.severity === 'danger' ? 'text-red-500' : 'text-yellow-600'"
            >
              {{ alert.severity === 'danger' ? '严重异常' : '健康提醒' }}
            </span>
            <span class="text-xs text-gray-500">
              {{ new Date(alert.timestamp).toLocaleString('zh-CN') }}
            </span>
          </div>
          <p class="text-sm text-gray-600">{{ alert.message }}</p>
        </div>
        
        <button 
          @click="dismissAlert(alert.id)"
          class="text-gray-500 hover:text-gray-900 transition-colors flex-shrink-0"
        >
          <X class="w-4 h-4" />
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.health-alerts {
  width: 100%;
}
</style>
