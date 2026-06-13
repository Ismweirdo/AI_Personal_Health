<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import AppLayout from '../../components/common/AppLayout.vue';
import GlassCard from '../../components/common/GlassCard.vue';
import { createGoal, deleteGoal, getGoals, updateGoal, type HealthGoalResponse } from '../../api/goals';
import { getLabelByType, getUnitByType, healthDataTypes } from '../../api/health';
import { Flag, Edit2, Trash2, Target, Zap } from 'lucide-vue-next';

const goals = ref<HealthGoalResponse[]>([]);
const editingId = ref<number | null>(null);
const togglingId = ref<number | null>(null);
const form = ref({
  type: 'steps',
  targetValue: 8000,
  unit: '步',
  period: 'daily' as 'daily' | 'weekly' | 'monthly',
  enabled: true
});

const pageTitle = computed(() => editingId.value ? '编辑健康目标' : '新增健康目标');

const showGoalSuccess = (message: string) => {
  ElMessage.closeAll();
  ElMessage.success({
    message,
    duration: 500,
    showClose: false
  });
};

const loadGoals = async () => {
  const res = await getGoals();
  goals.value = res.data || [];
};

onMounted(loadGoals);

const handleTypeChange = () => {
  form.value.unit = getUnitByType(form.value.type);
};

const resetForm = () => {
  editingId.value = null;
  form.value = {
    type: 'steps',
    targetValue: 8000,
    unit: '步',
    period: 'daily',
    enabled: true
  };
};

const handleSubmit = async () => {
  const payload = { ...form.value };
  if (editingId.value) {
    await updateGoal(editingId.value, payload);
    showGoalSuccess('目标更新成功');
  } else {
    await createGoal(payload);
    showGoalSuccess('目标创建成功');
  }
  resetForm();
  await loadGoals();
};

const handleEdit = (goal: HealthGoalResponse) => {
  editingId.value = goal.id;
  form.value = {
    type: goal.type,
    targetValue: goal.targetValue,
    unit: goal.unit || getUnitByType(goal.type),
    period: goal.period as 'daily' | 'weekly' | 'monthly',
    enabled: goal.enabled
  };
};

const handleDelete = async (id: number) => {
  await deleteGoal(id);
  showGoalSuccess('目标删除成功');
  await loadGoals();
};

const handleToggleEnabled = async (goal: HealthGoalResponse) => {
  try {
    togglingId.value = goal.id;
    const nextEnabled = !goal.enabled;
    await updateGoal(goal.id, {
      type: goal.type,
      targetValue: goal.targetValue,
      unit: goal.unit,
      period: goal.period as 'daily' | 'weekly' | 'monthly',
      enabled: nextEnabled
    });
    if (editingId.value === goal.id) {
      form.value.enabled = nextEnabled;
    }
    showGoalSuccess(nextEnabled ? '已启用目标追踪' : '已停用目标追踪');
    await loadGoals();
  } finally {
    togglingId.value = null;
  }
};

const getProgressClass = (progress: number) => {
  if (progress >= 100) return 'bg-gradient-to-r from-emerald-400 to-green-400';
  if (progress >= 70) return 'bg-gradient-to-r from-pink-400 to-pink-500';
  return 'bg-gradient-to-r from-amber-400 to-orange-400';
};

const periodLabel = (period: string) => ({ daily: '每日', weekly: '每周', monthly: '每月' }[period] || period);
</script>

<template>
  <AppLayout title="目标管理" subtitle="设置日常健康目标并持续追踪进度">
    <div class="grid grid-cols-1 xl:grid-cols-3 gap-6">
      <!-- 表单卡片 -->
      <GlassCard 
        class="p-6 xl:col-span-1" 
        animation-delay="100ms"
        animation-type="fade-in"
      >
        <div class="flex items-center gap-3 mb-5">
          <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-pink-400 to-pink-500 flex items-center justify-center">
            <Target class="w-5 h-5 text-gray-900" />
          </div>
          <h3 class="text-xl font-semibold text-gray-900">{{ pageTitle }}</h3>
        </div>
        
        <div class="space-y-4">
          <div class="animate-fade-in-up" style="animation-delay: 200ms">
            <label class="block text-sm text-gray-500 mb-2">目标类型</label>
            <el-select
              v-model="form.type"
              class="bili-el-select w-full"
              popper-class="bili-select-dropdown"
              @change="handleTypeChange"
            >
              <el-option
                v-for="item in healthDataTypes"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </div>
          
          <div class="animate-fade-in-up" style="animation-delay: 300ms">
            <label class="block text-sm text-gray-500 mb-2">目标值</label>
            <input 
              v-model.number="form.targetValue" 
              type="number" 
              class="bili-input w-full px-4 py-3 rounded-xl outline-none"
              :placeholder="`目标值（单位：${form.unit}）`"
            />
          </div>
          
          <div class="animate-fade-in-up" style="animation-delay: 400ms">
            <label class="block text-sm text-gray-500 mb-2">周期</label>
            <el-select
              v-model="form.period"
              class="bili-el-select w-full"
              popper-class="bili-select-dropdown"
            >
              <el-option label="每日" value="daily" />
              <el-option label="每周" value="weekly" />
              <el-option label="每月" value="monthly" />
            </el-select>
            <p class="mt-2 text-xs text-gray-500">
              周报仅关联每周目标，月报仅关联每月目标；每日目标只在目标页内跟踪。
            </p>
          </div>
          
          <div class="animate-fade-in-up" style="animation-delay: 500ms">
            <div class="flex items-center justify-between gap-3 rounded-xl border border-gray-200 bg-gray-50 px-4 py-3">
              <div>
                <p class="text-gray-900">启用目标追踪</p>
                <p class="mt-1 text-xs" :class="form.enabled ? 'text-pink-500' : 'text-gray-500'">
                  {{ form.enabled ? '当前状态：已启用' : '当前状态：已停用' }}
                </p>
              </div>
              <button
                type="button"
                class="relative h-6 w-11 rounded-full transition-colors duration-300"
                :class="form.enabled ? 'bg-pink-400' : 'bg-gray-200'"
                @click="form.enabled = !form.enabled"
                :aria-pressed="form.enabled"
                :title="form.enabled ? '点击停用目标追踪' : '点击启用目标追踪'"
              >
                <span
                  class="absolute top-1 h-4 w-4 rounded-full bg-white transition-all duration-300"
                  :class="form.enabled ? 'left-6' : 'left-1'"
                />
              </button>
            </div>
          </div>
          
          <div class="flex gap-3 mt-6 animate-fade-in-up" style="animation-delay: 600ms">
            <button 
              class="flex-1 py-3 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 text-gray-900 font-semibold transition-all duration-300 hover:shadow-lg hover:shadow-pink-400/25 hover:scale-[1.02] active:scale-[0.98]"
              @click="handleSubmit"
            >
              {{ editingId ? '保存修改' : '创建目标' }}
            </button>
            <button 
              class="px-6 py-3 rounded-xl bg-gray-50 border border-gray-200 text-gray-600 transition-all duration-300 hover:bg-gray-200"
              @click="resetForm"
            >
              重置
            </button>
          </div>
        </div>
      </GlassCard>

      <!-- 目标列表 -->
      <div class="xl:col-span-2 space-y-4">
        <GlassCard
          v-for="(goal, index) in goals"
          :key="goal.id"
          class="p-6 group"
          :animation-delay="`${200 + index * 100}ms`"
        >
          <div class="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
            <div class="flex-1">
              <div class="flex items-center gap-3 mb-2">
                <div class="w-10 h-10 rounded-xl flex items-center justify-center" :class="goal.enabled ? 'bg-gradient-to-br from-indigo-500/20 to-purple-500/20' : 'bg-gray-500/20'">
                  <Flag class="w-5 h-5" :class="goal.enabled ? 'text-pink-400' : 'text-gray-500'" />
                </div>
                <div>
                  <h3 class="text-lg font-semibold text-gray-900">{{ getLabelByType(goal.type) }}</h3>
                  <div class="flex items-center gap-2 mt-1">
                    <span class="px-2 py-0.5 rounded-full text-xs bg-gray-200 text-gray-600">{{ periodLabel(goal.period) }}</span>
                    <span 
                      v-if="!goal.enabled" 
                      class="px-2 py-0.5 rounded-full text-xs bg-red-500/10 text-red-300"
                    >
                      已停用
                    </span>
                    <span 
                      v-if="goal.progress >= 100" 
                      class="px-2 py-0.5 rounded-full text-xs bg-emerald-500/10 text-emerald-300 flex items-center gap-1"
                    >
                      <Zap class="w-3 h-3" />
                      已完成
                    </span>
                  </div>
                </div>
              </div>
              <p class="text-gray-500 ml-13">
                当前 <span class="text-gray-900 font-semibold">{{ goal.currentValue }}</span> / 目标 <span class="text-pink-500 font-semibold">{{ goal.targetValue }}</span> {{ goal.unit }}
              </p>
            </div>
            <div class="flex gap-2">
              <button
                class="px-3 py-2 rounded-lg bg-gray-50 hover:bg-indigo-500/20 text-gray-600 hover:text-amber-400 transition-all duration-300"
                @click="handleToggleEnabled(goal)"
                :disabled="togglingId === goal.id"
                :title="goal.enabled ? '停用目标追踪' : '启用目标追踪'"
              >
                {{ togglingId === goal.id ? '处理中...' : goal.enabled ? '停用追踪' : '启用追踪' }}
              </button>
              <button 
                class="p-2 rounded-lg bg-gray-50 hover:bg-indigo-500/20 text-gray-500 hover:text-pink-500 transition-all duration-300 hover:scale-110"
                @click="handleEdit(goal)"
                title="编辑"
              >
                <Edit2 class="w-5 h-5" />
              </button>
              <button 
                class="p-2 rounded-lg bg-gray-50 hover:bg-red-500/20 text-gray-500 hover:text-red-300 transition-all duration-300 hover:scale-110"
                @click="handleDelete(goal.id)"
                title="删除"
              >
                <Trash2 class="w-5 h-5" />
              </button>
            </div>
          </div>
          
          <div class="mt-4 pt-4 border-t border-gray-100">
            <div class="flex items-center justify-between text-sm text-gray-600 mb-2">
              <span>完成度</span>
              <span class="font-semibold" :class="goal.progress >= 100 ? 'text-emerald-400' : goal.progress >= 70 ? 'text-pink-400' : 'text-amber-400'">
                {{ goal.progress }}%
              </span>
            </div>
            <div class="w-full h-3 rounded-full bg-gray-200 overflow-hidden">
              <div 
                class="h-full rounded-full transition-all duration-1000 ease-out relative overflow-hidden" 
                :class="getProgressClass(goal.progress)"
                :style="{ width: `${Math.min(goal.progress, 100)}%` }"
              >
                <div class="absolute inset-0 bg-gradient-to-r from-transparent via-white/30 to-transparent animate-shimmer"></div>
              </div>
            </div>
            <p class="mt-2 text-sm text-gray-500">
              距离完成还差 <span class="text-amber-400 font-semibold">{{ goal.remainingValue }}</span> {{ goal.unit }}
            </p>
          </div>
        </GlassCard>
        
        <!-- 空状态 -->
        <GlassCard 
          v-if="goals.length === 0" 
          class="p-12 text-center"
          animation-delay="300ms"
        >
          <div class="w-16 h-16 rounded-2xl bg-gray-50 mx-auto mb-4 flex items-center justify-center">
            <Target class="w-8 h-8 text-gray-500" />
          </div>
          <h3 class="text-lg font-semibold text-gray-500 mb-2">暂无健康目标</h3>
          <p class="text-gray-500">点击左侧表单创建您的第一个健康目标</p>
        </GlassCard>
      </div>
    </div>
  </AppLayout>
</template>

<style scoped>
.ml-13 {
  margin-left: 3.25rem;
}

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
