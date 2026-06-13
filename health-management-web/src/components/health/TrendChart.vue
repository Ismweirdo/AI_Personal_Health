<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { LineChart } from 'echarts/charts';
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  DataZoomComponent
} from 'echarts/components';
import VChart from 'vue-echarts';
import { Activity, TrendingUp } from 'lucide-vue-next';
import { ElSelect, ElOption } from 'element-plus';
import { getHealthDataTrend, healthDataTypes, getLabelByType, getUnitByType } from '../../api/health';
import type { HealthDataResponse } from '../../api/health';

use([
  CanvasRenderer,
  LineChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  DataZoomComponent
]);

const props = defineProps<{
  refreshTrigger?: number;
}>();

const selectedType = ref('steps');
const selectedPeriod = ref('week');
const loading = ref(false);
const trendData = ref<HealthDataResponse[]>([]);

// 图表配置
const chartOption = computed(() => {
  const dates = trendData.value.map(item => {
    const date = new Date(item.recordDate);
    return `${date.getMonth() + 1}/${date.getDate()}`;
  });
  
  const values = trendData.value.map(item => item.value);
  const unit = getUnitByType(selectedType.value);
  const label = getLabelByType(selectedType.value);
  
  return {
    backgroundColor: 'transparent',
    grid: {
      left: '3%',
      right: '4%',
      bottom: '15%',
      top: '10%',
      containLabel: true
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 253, 246, 0.98)',
      borderColor: 'rgba(121, 104, 78, 0.22)',
      textStyle: {
        color: '#3E3A35'
      },
      formatter: (params: any) => {
        const data = params[0];
        return `
          <div style="padding: 8px;">
            <div style="color: #6B7280; margin-bottom: 4px;">${data.name}</div>
            <div style="font-size: 16px; font-weight: bold;">
              ${data.value} ${unit}
            </div>
          </div>
        `;
      }
    },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: {
      lineStyle: {
        color: '#E8E8ED'
      }
      },
      axisLabel: {
        color: '#746C61'
      }
    },
    yAxis: {
      type: 'value',
      axisLine: {
        show: false
      },
      splitLine: {
        lineStyle: {
          color: '#E9E1D4'
        }
      },
      axisLabel: {
        color: '#746C61',
        formatter: `{value} ${unit}`
      }
    },
    dataZoom: [
      {
        type: 'inside',
        start: 0,
        end: 100
      }
    ],
    series: [
      {
        name: label,
        type: 'line',
        data: values,
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        lineStyle: {
          width: 3,
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 1,
            y2: 0,
            colorStops: [
              { offset: 0, color: '#3B6B57' },
              { offset: 1, color: '#6B705C' }
            ]
          }
        },
        itemStyle: {
          color: '#3B6B57',
          borderWidth: 2,
          borderColor: '#fffdf6'
        },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(59, 107, 87, 0.22)' },
              { offset: 1, color: 'rgba(59, 107, 87, 0.03)' }
            ]
          }
        }
      }
    ]
  };
});

// 统计数据
const stats = computed(() => {
  if (trendData.value.length === 0) return null;
  
  const values = trendData.value.map(item => item.value);
  const sum = values.reduce((a, b) => a + b, 0);
  const avg = sum / values.length;
  const max = Math.max(...values);
  const min = Math.min(...values);
  
  return {
    avg: avg.toFixed(1),
    max,
    min,
    count: values.length
  };
});

// 加载趋势数据
const loadTrendData = async () => {
  loading.value = true;
  try {
    const res = await getHealthDataTrend(
      selectedType.value,
      selectedPeriod.value
    );
    trendData.value = res.data || [];
  } catch (error) {
    console.error('加载趋势数据失败:', error);
  } finally {
    loading.value = false;
  }
};

// 监听变化
watch(() => props.refreshTrigger, loadTrendData, { immediate: true });
watch([selectedType, selectedPeriod], loadTrendData);

onMounted(loadTrendData);
</script>

<template>
  <div class="trend-chart">
    <!-- 标题和筛选 -->
    <div class="flex flex-wrap items-center justify-between gap-4 mb-6">
      <h3 class="text-lg font-semibold text-gray-900 flex items-center gap-2">
        <Activity class="w-5 h-5 text-ink-pine" />
        健康趋势分析
      </h3>
      
      <div class="flex items-center gap-3">
        <!-- 类型选择 -->
        <el-select
          v-model="selectedType"
          class="bili-el-select text-sm min-w-[9rem]"
          popper-class="bili-select-dropdown"
        >
          <el-option 
            v-for="t in healthDataTypes" 
            :key="t.value" 
            :label="t.label" 
            :value="t.value"
          />
        </el-select>
        
        <!-- 时间范围 -->
        <el-select
          v-model="selectedPeriod"
          class="bili-el-select text-sm min-w-[9rem]"
          popper-class="bili-select-dropdown"
        >
          <el-option 
            v-for="option in [{ value: 'week', label: '最近一周' }, { value: 'month', label: '最近一月' }, { value: 'quarter', label: '最近三月' }]" 
            :key="option.value" 
            :label="option.label" 
            :value="option.value"
          />
        </el-select>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div v-if="stats" class="grid grid-cols-4 gap-3 mb-6">
      <div class="glass-card p-3 rounded-xl text-center">
        <div class="text-2xl font-bold text-gray-900">{{ stats.avg }}</div>
        <div class="text-xs text-gray-500">平均值</div>
      </div>
      <div class="glass-card p-3 rounded-xl text-center">
        <div class="text-2xl font-bold text-ink-pine">{{ stats.max }}</div>
        <div class="text-xs text-gray-500">最高值</div>
      </div>
      <div class="glass-card p-3 rounded-xl text-center">
        <div class="text-2xl font-bold text-ink-gold">{{ stats.min }}</div>
        <div class="text-xs text-gray-500">最低值</div>
      </div>
      <div class="glass-card p-3 rounded-xl text-center">
        <div class="text-2xl font-bold text-ink-cinnabar">{{ stats.count }}</div>
        <div class="text-xs text-gray-500">记录数</div>
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="text-center py-12">
      <div class="animate-spin w-8 h-8 border-2 border-ink-pine border-t-transparent rounded-full mx-auto"></div>
      <p class="text-gray-500 mt-2">加载中...</p>
    </div>

    <!-- 空状态 -->
    <div v-else-if="trendData.length === 0" class="text-center py-12">
      <div class="w-16 h-16 bg-gray-100 rounded-full flex items-center justify-center mx-auto mb-4">
        <TrendingUp class="w-8 h-8 text-gray-500" />
      </div>
      <p class="text-gray-500">暂无趋势数据</p>
      <p class="text-gray-500 text-sm mt-1">记录更多数据以查看趋势</p>
    </div>

    <!-- 图表 -->
    <div v-else class="h-80">
      <v-chart class="w-full h-full" :option="chartOption" autoresize />
    </div>
  </div>
</template>

<style scoped>
.trend-chart {
  width: 100%;
}

.glass-card {
  background: rgba(255, 253, 246, 0.82);
  border: 1px solid rgba(121, 104, 78, 0.18);
  backdrop-filter: none;
}
</style>
