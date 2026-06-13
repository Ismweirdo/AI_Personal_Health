<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import AppLayout from '../../components/common/AppLayout.vue';
import GlassCard from '../../components/common/GlassCard.vue';
import { getGoals, type HealthGoalResponse } from '../../api/goals';
import {
  getHealthReport,
  type HealthMetricReportItem,
  type HealthReportResponse
} from '../../api/reports';
import { getMetricConfig } from '../../config/healthMetrics';
import { FileText, TrendingUp, Award, AlertCircle, CheckCircle2 } from 'lucide-vue-next';

const period = ref<'weekly' | 'monthly'>('weekly');
const report = ref<HealthReportResponse | null>(null);
const linkedGoals = ref<HealthGoalResponse[]>([]);
const loading = ref(false);

const fetchCurrentReport = async () => {
  loading.value = true;
  try {
    const res = await getHealthReport(period.value);
    report.value = res.data;
  } finally {
    loading.value = false;
  }
};

const loadLinkedGoals = async () => {
  try {
    const res = await getGoals(true);
    linkedGoals.value = res.data || [];
  } catch {
    linkedGoals.value = [];
  }
};

const showCurrentReport = async () => {
  await fetchCurrentReport();
  await loadLinkedGoals();
};

watch(period, () => {
  void showCurrentReport();
}, { immediate: true });

const getDisplayUnit = (type: string, unit?: string) => {
  if (unit && unit.trim() && unit.trim() !== '?') {
    return unit;
  }
  return getMetricConfig(type)?.unit || '';
};

const isTotalMetric = (metric: HealthMetricReportItem) => metric.aggregationMode === 'period_total';

const getLatestLabel = (metric: HealthMetricReportItem) => (
  isTotalMetric(metric) ? '最近日值' : '最新值'
);

const getRangeLabel = (metric: HealthMetricReportItem) => (
  isTotalMetric(metric) ? '每日范围' : '范围'
);

const getAverageLabel = (metric: HealthMetricReportItem) => (
  isTotalMetric(metric) ? '周期平均' : '周期平均'
);

const getPrimaryMetricLabel = (metric: HealthMetricReportItem) => (
  isTotalMetric(metric) ? '周期总量' : '周期平均'
);

const getPrimaryMetricValue = (metric: HealthMetricReportItem) => (
  isTotalMetric(metric) ? metric.summaryValue : metric.averageValue
);

const getTrendDotClass = (trend: string) => (
  trend.includes('上升') ? 'bg-emerald-400' : trend.includes('下降') ? 'bg-amber-400' : 'bg-slate-400'
);

const getTrendCardClass = (trend: string) => (
  trend.includes('上升')
    ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
    : trend.includes('下降')
      ? 'border-amber-200 bg-amber-50 text-amber-700'
      : 'border-gray-200 bg-gray-50 text-gray-700'
);

const getReportRangeDays = () => {
  if (!report.value?.startDate || !report.value?.endDate) {
    return 0;
  }
  const start = new Date(report.value.startDate);
  const end = new Date(report.value.endDate);
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) {
    return 0;
  }
  const diff = end.getTime() - start.getTime();
  return Math.max(1, Math.floor(diff / (1000 * 60 * 60 * 24)) + 1);
};

const getActiveDays = (metric: HealthMetricReportItem) => {
  if (typeof metric.activeDays === 'number' && metric.activeDays > 0) {
    return metric.activeDays;
  }
  const rangeDays = getReportRangeDays();
  if (typeof metric.recordCount === 'number' && metric.recordCount > 0) {
    return rangeDays > 0 ? Math.min(metric.recordCount, rangeDays) : metric.recordCount;
  }
  return rangeDays > 0 ? rangeDays : 0;
};

const getGoalLabel = (type: string) => getMetricConfig(type)?.label || type;

const getSourceLabel = (sourceType?: string) => (
  sourceType === 'snapshot' ? '历史快照' : '当前实时报告'
);

const getGoalPeriodLabel = (period?: string) => (
  period === 'daily' ? '每日追踪' : period === 'weekly' ? '每周目标' : period === 'monthly' ? '每月目标' : period || '目标'
);

const getGoalTrackingHint = (goal: { period?: string }) => (
  goal.period === 'daily' ? '按目标自身的每日追踪结果评估' : goal.period === 'weekly'
    ? '按目标自身的每周追踪结果评估'
    : goal.period === 'monthly'
      ? '按目标自身的每月追踪结果评估'
      : '按目标自身周期追踪结果评估'
);

const displayedGoals = computed(() => {
  if (report.value?.goals?.length) {
    return report.value.goals;
  }
  return linkedGoals.value;
});

const getPeriodText = (periodValue?: string) => (
  periodValue === 'monthly' ? '月报' : '周报'
);

const escapeCsvCell = (value: unknown) => {
  const text = String(value ?? '');
  if (text.includes('"') || text.includes(',') || text.includes('\n')) {
    return `"${text.replace(/"/g, '""')}"`;
  }
  return text;
};

const buildCsvLine = (cells: unknown[]) => cells.map(escapeCsvCell).join(',');

const buildReportCsv = () => {
  if (!report.value) {
    return '';
  }

  const lines: string[] = [];
  const currentReport = report.value;
  const exportTime = new Date().toLocaleString('zh-CN', { hour12: false });

  lines.push(buildCsvLine(['报告信息']));
  lines.push(buildCsvLine(['字段', '值']));
  lines.push(buildCsvLine(['报告类型', getPeriodText(currentReport.period)]));
  lines.push(buildCsvLine(['数据来源', getSourceLabel(currentReport.sourceType)]));
  lines.push(buildCsvLine(['统计口径', '按天聚合后汇总']));
  lines.push(buildCsvLine(['统计区间', `${currentReport.startDate} 至 ${currentReport.endDate}`]));
  lines.push(buildCsvLine(['报告生成时间', currentReport.generatedAt]));
  lines.push(buildCsvLine(['导出时间', exportTime]));
  lines.push('');

  lines.push(buildCsvLine(['指标摘要']));
  lines.push(buildCsvLine([
    '指标',
    '状态',
    '周期总量',
    '周期平均',
    '最新值/最近日值',
    '范围',
    '趋势',
    '活跃天数',
    '原始记录数',
    '单位'
  ]));
  currentReport.metrics.forEach((metric) => {
    lines.push(buildCsvLine([
      metric.label,
      metric.status,
      isTotalMetric(metric) ? metric.summaryValue : '',
      metric.averageValue,
      metric.latestValue,
      `${metric.minValue} - ${metric.maxValue}`,
      metric.trend,
      getActiveDays(metric),
      metric.recordCount,
      getDisplayUnit(metric.type, metric.unit)
    ]));
  });
  lines.push('');

  lines.push(buildCsvLine(['目标达成']));
  lines.push(buildCsvLine([
    '目标',
    '周期',
    '当前值',
    '目标值',
    '完成度',
    '剩余值',
    '单位',
    '追踪说明'
  ]));
  displayedGoals.value.forEach((goal) => {
    lines.push(buildCsvLine([
      getGoalLabel(goal.type),
      getGoalPeriodLabel(goal.period),
      goal.currentValue,
      goal.targetValue,
      `${goal.progress}%`,
      goal.remainingValue,
      goal.unit || '',
      getGoalTrackingHint(goal)
    ]));
  });
  lines.push('');

  lines.push(buildCsvLine(['报告亮点']));
  lines.push(buildCsvLine(['序号', '内容']));
  currentReport.highlights.forEach((item, index) => {
    lines.push(buildCsvLine([index + 1, item]));
  });

  return '\uFEFF' + lines.join('\r\n');
};

const exportReportCsv = () => {
  if (!report.value) {
    ElMessage.warning('当前没有可导出的报告');
    return;
  }

  const csvContent = buildReportCsv();
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  const reportType = getPeriodText(report.value.period);
  link.href = url;
  link.download = `健康${reportType}_${report.value.startDate}_${report.value.endDate}.csv`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
  ElMessage.success({
    message: '报告已导出为 CSV',
    duration: 1000
  });
};
</script>

<template>
  <AppLayout title="健康周报与月报" subtitle="按周期汇总指标变化与目标进度">
    <!-- 周期选择 -->
    <GlassCard 
      class="p-6 mb-6" 
      animation-delay="100ms"
      animation-type="fade-in-down"
    >
      <div class="flex flex-wrap items-center gap-4">
        <div class="flex gap-2">
          <button 
            class="px-6 py-3 rounded-xl font-semibold transition-all duration-300"
            :class="period === 'weekly' 
              ? 'bg-gradient-to-r from-pink-400 to-pink-500 text-gray-900 shadow-lg shadow-indigo-500/30' 
              : 'bg-gray-50 text-gray-700 hover:bg-gray-100 border border-gray-200'"
            @click="period = 'weekly'"
          >
            <span class="flex items-center gap-2">
              <span class="w-2 h-2 rounded-full" :class="period === 'weekly' ? 'bg-white/80' : 'bg-gray-500'"></span>
              周报
            </span>
          </button>
          <button 
            class="px-6 py-3 rounded-xl font-semibold transition-all duration-300"
            :class="period === 'monthly' 
              ? 'bg-gradient-to-r from-pink-400 to-pink-500 text-gray-900 shadow-lg shadow-indigo-500/30' 
              : 'bg-gray-50 text-gray-700 hover:bg-gray-100 border border-gray-200'"
            @click="period = 'monthly'"
          >
            <span class="flex items-center gap-2">
              <span class="w-2 h-2 rounded-full" :class="period === 'monthly' ? 'bg-white/80' : 'bg-gray-500'"></span>
              月报
            </span>
          </button>
        </div>
        <button
          class="px-4 py-3 rounded-xl font-medium transition-all duration-300 bg-gray-50 text-gray-700 hover:bg-gray-100 border border-gray-200 disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="!report"
          @click="exportReportCsv"
        >
          导出 CSV
        </button>
        <span v-if="report" class="text-gray-500 text-sm animate-fade-in" style="animation-delay: 300ms">
          统计区间：{{ report.startDate }} 至 {{ report.endDate }}
        </span>
      </div>

      <div v-if="report" class="flex flex-wrap gap-2 mt-4">
        <span class="px-3 py-1 rounded-full text-xs bg-pink-50 text-pink-500 border border-pink-50">
          {{ getSourceLabel(report.sourceType) }}
        </span>
        <span class="px-3 py-1 rounded-full text-xs bg-cyan-50 text-cyan-700 border border-cyan-200">
          统计口径：按天聚合后汇总
        </span>
        <span class="px-3 py-1 rounded-full text-xs bg-gray-50 text-gray-600 border border-gray-200">
          生成时间：{{ report.generatedAt }}
        </span>
      </div>
    </GlassCard>

    <!-- 加载状态 -->
    <div v-if="loading" class="flex flex-col items-center justify-center py-20">
      <div class="relative">
        <div class="w-16 h-16 border-4 border-pink-50 rounded-full"></div>
        <div class="absolute inset-0 w-16 h-16 border-4 border-indigo-500 rounded-full border-t-transparent animate-spin"></div>
      </div>
      <p class="text-gray-500 mt-4">报告生成中...</p>
    </div>

    <!-- 报告内容 -->
    <template v-else-if="report">
      <div class="grid grid-cols-1 xl:grid-cols-3 gap-6">
        <!-- 指标摘要 -->
        <GlassCard 
          class="p-6 xl:col-span-2" 
          animation-delay="200ms"
        >
          <div class="flex items-center gap-3 mb-6">
            <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-indigo-500 to-purple-600 flex items-center justify-center">
              <TrendingUp class="w-5 h-5 text-gray-900" />
            </div>
            <h3 class="text-xl font-semibold text-gray-900">指标摘要</h3>
          </div>
          <div class="grid grid-cols-1 lg:grid-cols-2 gap-5">
            <div 
              v-for="(metric, index) in report.metrics" 
              :key="metric.type" 
              class="p-5 rounded-2xl bg-[rgba(255,253,246,0.88)] border border-gray-200 transition-all duration-300 hover:bg-gray-50 hover:shadow-lg hover:shadow-gray-200/70 group"
              :style="{ animationDelay: `${300 + index * 100}ms` }"
            >
              <div class="flex items-start justify-between gap-4 mb-4">
                <div class="min-w-0">
                  <p class="text-xs uppercase tracking-[0.2em] text-gray-500 mb-2">指标摘要</p>
                  <h4 class="text-gray-900 text-lg font-semibold group-hover:text-[var(--ink-pine-dark)] transition-colors">{{ metric.label }}</h4>
                </div>
                <span 
                  class="px-3 py-1 rounded-full text-xs font-medium flex items-center gap-1 flex-shrink-0"
                  :class="metric.status === '正常' 
                    ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' 
                    : 'bg-amber-50 text-amber-700 border border-amber-200'"
                >
                  <CheckCircle2 v-if="metric.status === '正常'" class="w-3 h-3" />
                  <AlertCircle v-else class="w-3 h-3" />
                  {{ metric.status }}
                </span>
              </div>

              <div class="rounded-2xl border border-[rgba(59,107,87,0.22)] bg-[rgba(59,107,87,0.08)] px-4 py-4 mb-4">
                <div class="text-xs text-gray-600 mb-2">{{ getPrimaryMetricLabel(metric) }}</div>
                <div class="flex items-end gap-2 flex-wrap">
                  <span class="text-3xl font-semibold text-gray-950 leading-none">{{ getPrimaryMetricValue(metric) }}</span>
                  <span class="text-sm font-medium text-gray-700 pb-0.5">{{ getDisplayUnit(metric.type, metric.unit) || ' ' }}</span>
                </div>
              </div>

              <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div class="rounded-xl border border-gray-200 bg-gray-50 px-3 py-3">
                  <div class="text-xs text-gray-500 mb-1">{{ getAverageLabel(metric) }}</div>
                  <div class="text-base font-medium text-gray-900">{{ metric.averageValue }} {{ getDisplayUnit(metric.type, metric.unit) }}</div>
                </div>
                <div class="rounded-xl border border-gray-200 bg-gray-50 px-3 py-3">
                  <div class="text-xs text-gray-500 mb-1">{{ getLatestLabel(metric) }}</div>
                  <div class="text-base font-medium text-gray-900">{{ metric.latestValue }} {{ getDisplayUnit(metric.type, metric.unit) }}</div>
                </div>
                <div class="rounded-xl border border-gray-200 bg-gray-50 px-3 py-3">
                  <div class="text-xs text-gray-500 mb-1">{{ getRangeLabel(metric) }}</div>
                  <div class="text-sm font-medium text-gray-900 leading-6">{{ metric.minValue }} - {{ metric.maxValue }} {{ getDisplayUnit(metric.type, metric.unit) }}</div>
                </div>
                <div class="rounded-xl border px-3 py-3" :class="getTrendCardClass(metric.trend)">
                  <div class="text-xs opacity-80 mb-1">趋势变化</div>
                  <div class="text-sm font-medium flex items-center gap-2">
                    <span class="w-1.5 h-1.5 rounded-full" :class="getTrendDotClass(metric.trend)"></span>
                    {{ metric.trend }}
                  </div>
                </div>
              </div>

              <div class="mt-4 flex flex-wrap gap-2">
                <span class="px-2.5 py-1 rounded-full text-xs bg-gray-50 text-gray-600 border border-gray-200">
                  活跃 {{ getActiveDays(metric) }} 天
                </span>
                <span class="px-2.5 py-1 rounded-full text-xs bg-gray-50 text-gray-600 border border-gray-200">
                  原始记录 {{ metric.recordCount }} 条
                </span>
                <span v-if="isTotalMetric(metric)" class="px-2.5 py-1 rounded-full text-xs bg-cyan-50 text-cyan-700 border border-cyan-200">
                  按天累计
                </span>
                <span v-else class="px-2.5 py-1 rounded-full text-xs bg-violet-50 text-violet-700 border border-violet-200">
                  按天均值
                </span>
              </div>
            </div>
          </div>
        </GlassCard>

        <!-- 目标达成 -->
        <GlassCard 
          class="p-6" 
          animation-delay="300ms"
        >
          <div class="flex items-center gap-3 mb-6">
            <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-amber-500 to-orange-600 flex items-center justify-center">
              <Award class="w-5 h-5 text-gray-900" />
            </div>
            <h3 class="text-xl font-semibold text-gray-900">目标达成</h3>
          </div>
          
          <div v-if="!displayedGoals.length" class="text-center py-8">
            <div class="w-12 h-12 rounded-xl bg-gray-50 mx-auto mb-3 flex items-center justify-center">
              <FileText class="w-6 h-6 text-gray-500" />
            </div>
            <p class="text-gray-500 text-sm">当前周期没有关联目标</p>
          </div>
          
          <div v-else class="space-y-4">
            <div 
              v-for="(goal, index) in displayedGoals" 
              :key="goal.id"
              class="group"
              :style="{ animationDelay: `${400 + index * 100}ms` }"
            >
              <div class="flex items-center justify-between text-sm mb-2">
                <div>
                  <span class="text-gray-600 group-hover:text-gray-900 transition-colors">{{ getGoalLabel(goal.type) }}</span>
                  <div class="mt-1 flex items-center gap-2">
                    <span class="px-2 py-0.5 rounded-full text-xs bg-gray-100 text-gray-700 border border-gray-200">
                      {{ getGoalPeriodLabel(goal.period) }}
                    </span>
                    <span class="text-xs text-gray-500">
                      {{ getGoalTrackingHint(goal) }}
                    </span>
                  </div>
                </div>
                <span 
                  class="font-semibold"
                  :class="goal.progress >= 100 ? 'text-emerald-700' : goal.progress >= 70 ? 'text-pink-700' : 'text-amber-700'"
                >
                  {{ goal.progress }}%
                </span>
              </div>
              <div class="w-full h-3 rounded-full bg-gray-100 overflow-hidden">
                <div 
                  class="h-full rounded-full transition-all duration-1000 ease-out relative overflow-hidden"
                  :class="goal.progress >= 100 
                    ? 'bg-gradient-to-r from-emerald-400 to-green-400' 
                    : goal.progress >= 70 
                      ? 'bg-gradient-to-r from-indigo-400 to-purple-400' 
                      : 'bg-gradient-to-r from-amber-400 to-orange-400'"
                  :style="{ width: `${Math.min(goal.progress, 100)}%` }"
                >
                  <div class="absolute inset-0 bg-gradient-to-r from-transparent via-white/30 to-transparent animate-shimmer"></div>
                </div>
              </div>
              <p class="mt-2 text-sm text-gray-500">
                当前 <span class="text-gray-900">{{ goal.currentValue }}</span> / 目标
                <span class="text-[var(--ink-pine-dark)] font-medium">{{ goal.targetValue }}</span> {{ goal.unit }}
              </p>
            </div>
          </div>
        </GlassCard>
      </div>

      <!-- 报告提炼 -->
      <div class="mt-6">
        <GlassCard 
          class="p-6" 
          animation-delay="500ms"
        >
          <div class="flex items-center gap-3 mb-4">
            <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-emerald-500 to-teal-600 flex items-center justify-center">
              <CheckCircle2 class="w-5 h-5 text-gray-900" />
            </div>
            <div>
              <h3 class="text-xl font-semibold text-gray-900">报告提炼</h3>
              <p class="text-sm text-gray-500 mt-1">提取本周期最值得关注的变化和结论</p>
            </div>
          </div>
          <ul class="grid grid-cols-1 lg:grid-cols-2 gap-3">
            <li 
              v-for="(item, index) in report.highlights" 
              :key="index" 
              class="p-4 rounded-2xl bg-gray-50 border border-gray-200 transition-all duration-300 hover:bg-gray-100 group flex items-start gap-3 min-h-[96px]"
              :style="{ animationDelay: `${600 + index * 100}ms` }"
            >
              <span class="flex-shrink-0 w-7 h-7 rounded-xl bg-emerald-50 border border-emerald-200 flex items-center justify-center mt-0.5 group-hover:scale-110 transition-transform text-sm font-semibold text-emerald-700">
                {{ index + 1 }}
              </span>
              <div class="min-w-0">
                <div class="text-xs uppercase tracking-[0.18em] text-emerald-700 mb-1">提炼 {{ index + 1 }}</div>
                <p class="text-gray-600 group-hover:text-gray-900 transition-colors leading-6">{{ item }}</p>
              </div>
            </li>
          </ul>
        </GlassCard>
      </div>
    </template>
  </AppLayout>
</template>

<style scoped>
@keyframes shimmer {
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(100%);
  }
}

.animate-shimmer {
  animation: shimmer 2s infinite;
}
</style>
