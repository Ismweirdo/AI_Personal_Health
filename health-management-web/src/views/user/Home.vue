<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { User, Brain, MessageSquare, Bell, Flag, FileText, TrendingUp, Heart, Activity, Thermometer } from 'lucide-vue-next';
import AppLayout from '../../components/common/AppLayout.vue';
import GlassCard from '../../components/common/GlassCard.vue';
import TrendChart from '../../components/health/TrendChart.vue';
import DataInputForm from '../../components/health/DataInputForm.vue';
import DataList from '../../components/health/DataList.vue';
import HealthAlerts from '../../components/health/HealthAlerts.vue';
import { getHealthDataList, type HealthDataResponse } from '../../api/health';
import { getGoals, type HealthGoalResponse } from '../../api/goals';

const router = useRouter();
const refreshTrigger = ref(0);
const todayHealthData = ref<HealthDataResponse[]>([]);
const goals = ref<HealthGoalResponse[]>([]);
const editingData = ref<HealthDataResponse | null>(null);
const username = ref('User');
const stats = computed(() => {
  const today = todayHealthData.value;
  const latestValue = (type: string) => today.find(item => item.type === type)?.value ?? 0;
  return {
    steps: latestValue('steps'),
    calories: latestValue('calories'),
    heartRate: latestValue('heart_rate'),
    sleepHours: latestValue('sleep')
  };
});

onMounted(() => {
  username.value = localStorage.getItem('username') || 'User';
  loadTodayData();
  loadGoals();
});

const loadTodayData = async () => {
  try {
    const today = new Date().toISOString().split('T')[0];
    const res = await getHealthDataList('', today, today);
    todayHealthData.value = res.data || [];
  } catch {
    // 静默失败
  }
};

const loadGoals = async () => {
  try {
    const res = await getGoals(true);
    goals.value = res.data || [];
  } catch {
    // 静默失败
  }
};

const handleDataChange = () => {
  editingData.value = null;
  refreshTrigger.value++;
  loadTodayData();
  loadGoals();
};

const handleEditData = (item: HealthDataResponse) => {
  editingData.value = item;
};

const quickActions = [
  { icon: Flag, label: '目标管理', path: '/goals', delay: '100ms' },
  { icon: Bell, label: '提醒通知', path: '/reminders', delay: '200ms' },
  { icon: FileText, label: '健康报告', path: '/reports', delay: '300ms' },
];

const healthMetrics = [
  { icon: TrendingUp, label: '今日步数', value: computed(() => stats.value.steps), unit: '步', color: 'text-ink-pine', delay: '400ms' },
  { icon: Activity, label: '消耗热量', value: computed(() => stats.value.calories), unit: 'kcal', color: 'text-amber-400', delay: '500ms' },
  { icon: Heart, label: '平均心率', value: computed(() => stats.value.heartRate), unit: 'bpm', color: 'text-red-500', delay: '600ms' },
  { icon: Thermometer, label: '睡眠时长', value: computed(() => stats.value.sleepHours), unit: '小时', color: 'text-ink-pine', delay: '700ms' },
];
</script>

<template>
  <AppLayout :title="`你好, ${username}!`" subtitle="这是您今天的健康概览">
    <template #header-left>
      <div class="flex items-center gap-4">
        <div class="relative">
          <div class="w-14 h-14 rounded-lg border border-[rgba(163,74,58,0.32)] bg-[rgba(163,74,58,0.08)] flex items-center justify-center animate-fade-in-up">
            <User class="w-7 h-7 text-[var(--ink-cinnabar)]" />
          </div>
        </div>
        <div>
          <h1 class="text-3xl md:text-4xl font-bold text-gray-900 animate-fade-in-up" style="animation-delay: 100ms">
            你好, {{ username }}!
          </h1>
          <p class="text-gray-500 animate-fade-in-up" style="animation-delay: 200ms">
            这是您今天的健康概览
          </p>
        </div>
      </div>
    </template>

    <!-- 健康警告 -->
    <HealthAlerts :refresh-trigger="refreshTrigger" />

    <!-- 快捷操作卡片 -->
    <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
      <GlassCard
        v-for="action in quickActions"
        :key="action.path"
        :animation-delay="action.delay"
        class="p-5 cursor-pointer group"
        @click="router.push(action.path)"
      >
        <div class="flex items-center gap-4">
          <div
            class="w-12 h-12 rounded-lg border border-[rgba(59,107,87,0.22)] bg-[rgba(59,107,87,0.08)] flex items-center justify-center transition-colors duration-200 group-hover:border-[var(--ink-pine)]"
          >
            <component :is="action.icon" class="w-6 h-6 text-[var(--ink-pine-dark)]" />
          </div>
          <div>
            <div class="text-sm text-gray-500">{{ action.label }}</div>
            <div v-if="action.path === '/goals'" class="text-xl font-semibold text-gray-900">
              {{ goals.length }} 个进行中目标
            </div>
            <div v-else class="text-xl font-semibold text-gray-900">管理日常{{ action.label }}</div>
          </div>
        </div>
      </GlassCard>
    </div>

    <!-- 健康指标卡片 -->
    <GlassCard class="mb-6 p-6">
      <h2 class="text-lg font-semibold text-gray-900 mb-4 flex items-center gap-2">
        <Activity class="w-5 h-5 text-pink-400" />
        今日健康数据
      </h2>
      <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div
          v-for="metric in healthMetrics"
          :key="metric.label"
          class="relative p-4 rounded-xl transition-all duration-300 hover:bg-gray-50 cursor-pointer group"
          :style="{ animationDelay: metric.delay }"
        >
          <div class="w-10 h-10 rounded-lg border border-[rgba(121,104,78,0.18)] bg-[rgba(255,253,246,0.72)] flex items-center justify-center mb-3 transition-colors group-hover:border-[var(--ink-pine)]">
            <component :is="metric.icon" class="w-5 h-5" :class="metric.color" />
          </div>
          <div class="text-sm text-gray-500 mb-1">{{ metric.label }}</div>
          <div class="text-2xl font-bold text-gray-900">
            {{ metric.value }}
            <span class="text-sm font-normal text-gray-400 ml-1">{{ metric.unit }}</span>
          </div>
        </div>
      </div>
    </GlassCard>

    <!-- AI助手卡片 -->
    <GlassCard
      class="mb-6 p-6 cursor-pointer group"
      @click="router.push('/ai-chat')"
      animation-delay="300ms"
    >
      <div class="flex items-center gap-4">
        <div class="relative">
          <div class="w-14 h-14 rounded-lg border border-[rgba(59,107,87,0.24)] bg-[rgba(59,107,87,0.08)] flex items-center justify-center transition-colors duration-200 group-hover:border-[var(--ink-pine)]">
            <Brain class="w-7 h-7 text-[var(--ink-pine-dark)]" />
          </div>
        </div>
        <div class="flex-1">
          <h3 class="text-lg font-semibold text-gray-900 mb-1 flex items-center gap-2">
            智能健康助手
            <span class="text-xs px-2 py-0.5 bg-[rgba(59,107,87,0.08)] text-[var(--ink-pine-dark)] rounded-full font-medium">可用</span>
          </h3>
          <p class="text-gray-500 text-sm">有健康问题？随时咨询AI助手，获取专业建议</p>
        </div>
        <div class="flex items-center gap-2">
          <MessageSquare class="w-5 h-5 text-pink-400 group-hover:animate-wave" />
          <span class="text-[var(--ink-pine-dark)] text-sm font-medium group-hover:translate-x-1 transition-transform">点击咨询</span>
        </div>
      </div>
    </GlassCard>

    <!-- 图表和数据输入 -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <GlassCard class="lg:col-span-2 p-6" animation-delay="400ms">
        <h2 class="text-lg font-semibold text-gray-900 mb-4 flex items-center gap-2">
          <TrendingUp class="w-5 h-5 text-pink-400" />
          健康趋势
        </h2>
        <TrendChart :refresh-trigger="refreshTrigger" />
      </GlassCard>
      <GlassCard class="p-6" animation-delay="500ms">
        <h2 class="text-lg font-semibold text-gray-900 mb-4">记录健康数据</h2>
        <DataInputForm
          :editing-data="editingData"
          @success="handleDataChange"
          @cancel="editingData = null"
        />
      </GlassCard>
    </div>

    <!-- 今日数据列表 -->
    <GlassCard class="mt-6 p-6" animation-delay="600ms">
      <div class="flex items-center justify-between mb-4">
        <h2 class="text-lg font-semibold text-gray-900">今日记录</h2>
        <span class="text-sm text-gray-500">{{ todayHealthData.length }} 条记录</span>
      </div>
      <DataList
        :refresh-trigger="refreshTrigger"
        @deleted="handleDataChange"
        @changed="handleDataChange"
        @edit="handleEditData"
      />
    </GlassCard>
  </AppLayout>
</template>
