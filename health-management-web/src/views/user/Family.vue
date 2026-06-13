<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  Bell,
  Crown,
  Flag,
  HeartPulse,
  Home,
  MailPlus,
  Shield,
  Trash2,
  UserPlus,
  Users
} from 'lucide-vue-next';
import AppLayout from '../../components/common/AppLayout.vue';
import GlassCard from '../../components/common/GlassCard.vue';
import {
  cancelInvitation,
  createFamily,
  createFamilyInvitation,
  dissolveFamily,
  expandFamily,
  getFamily,
  getFamilyChildren,
  getFamilyInvitations,
  getMyFamilies,
  removeFamilyMember,
  transferFamilyCreator,
  updateFamilyMemberRole,
  type FamilyChildResponse,
  type FamilyGroupResponse,
  type FamilyInvitationResponse,
  type FamilyMemberResponse,
  type FamilyRole
} from '../../api/family';
import { createGoal, deleteGoal, getGoalsForUser, type HealthGoalResponse } from '../../api/goals';
import {
  createReminderRule,
  deleteReminderRule,
  getReminderRulesForUser,
  type ReminderRuleResponse
} from '../../api/reminders';
import { getHealthDataList, getLabelByType, getUnitByType, healthDataTypes, type HealthDataResponse } from '../../api/health';
import { formatDate } from '../../lib/utils';

const families = ref<FamilyGroupResponse[]>([]);
const selectedFamilyId = ref<number | null>(null);
const selectedFamily = ref<FamilyGroupResponse | null>(null);
const members = ref<FamilyMemberResponse[]>([]);
const invitations = ref<FamilyInvitationResponse[]>([]);
const children = ref<FamilyChildResponse[]>([]);
const selectedChildId = ref<number | null>(null);
const childHealthData = ref<HealthDataResponse[]>([]);
const childGoals = ref<HealthGoalResponse[]>([]);
const childRules = ref<ReminderRuleResponse[]>([]);
const isLoading = ref(false);

const familyForm = ref({ name: '我的家庭' });
const inviteForm = ref({
  inviteePhone: '',
  inviteeRole: 'child' as FamilyRole
});
const goalForm = ref({
  type: 'steps',
  targetValue: 8000,
  unit: '步',
  period: 'daily' as 'daily' | 'weekly' | 'monthly',
  enabled: true
});
const reminderForm = ref({
  title: '',
  type: 'custom',
  message: '',
  frequency: 'daily' as 'daily' | 'weekly' | 'once',
  remindTime: '09:00',
  remindDate: '',
  weeklyDay: 1,
  enabled: true
});

const selectedChild = computed(() => children.value.find(child => child.userId === selectedChildId.value) || null);
const canInvite = computed(() => selectedFamily.value?.myRole === 'parent');
const isCreator = computed(() => Boolean(selectedFamily.value?.creator));
const canManageChildren = computed(() => selectedFamily.value?.myRole === 'parent');
const canExpandFamily = computed(() => {
  if (!isCreator.value || !selectedFamily.value) {
    return false;
  }
  const maxMembers = selectedFamily.value.maxMembers || 5;
  const memberCount = selectedFamily.value.memberCount || members.value.length;
  return maxMembers < 10 && memberCount >= maxMembers;
});
const pendingInvitations = computed(() => invitations.value.filter(item => item.status === 'pending' || item.status === 'approval_pending'));

const roleLabel = (role?: string) => role === 'parent' ? '家长' : '儿童';
const statusLabel = (status?: string) => ({
  pending: '待接受',
  approval_pending: '待创建者审批',
  accepted: '已接受',
  canceled: '已取消',
  expired: '已过期',
  rejected: '已拒绝',
  approval_rejected: '审批已拒绝',
  active: '有效'
}[status || ''] || status || '-');
const periodLabel = (period: string) => ({ daily: '每日', weekly: '每周', monthly: '每月' }[period] || period);
const frequencyLabel = (frequency: string) => ({ daily: '每日', weekly: '每周', once: '单次' }[frequency] || frequency);

const loadFamilies = async () => {
  const res = await getMyFamilies();
  families.value = res.data || [];
  if (!selectedFamilyId.value && families.value.length) {
    selectedFamilyId.value = families.value[0].id;
  }
};

const loadSelectedFamily = async () => {
  if (!selectedFamilyId.value) {
    selectedFamily.value = null;
    members.value = [];
    invitations.value = [];
    return;
  }

  const familyRes = await getFamily(selectedFamilyId.value);
  selectedFamily.value = familyRes.data;
  members.value = familyRes.data.members || [];
  if (familyRes.data.myRole === 'parent') {
    const invitationRes = await getFamilyInvitations(selectedFamilyId.value);
    invitations.value = invitationRes.data || [];
  } else {
    invitations.value = [];
  }
};

const loadChildren = async () => {
  const res = await getFamilyChildren();
  children.value = res.data || [];
  if (!selectedChildId.value && children.value.length) {
    selectedChildId.value = children.value[0].userId;
  }
};

const loadChildWorkspace = async () => {
  if (!selectedChildId.value) {
    childHealthData.value = [];
    childGoals.value = [];
    childRules.value = [];
    return;
  }

  const [dataRes, goalsRes, rulesRes] = await Promise.all([
    getHealthDataList('', undefined, undefined, selectedChildId.value),
    getGoalsForUser(undefined, selectedChildId.value),
    getReminderRulesForUser(selectedChildId.value)
  ]);
  childHealthData.value = (dataRes.data || []).slice(0, 8);
  childGoals.value = goalsRes.data || [];
  childRules.value = rulesRes.data || [];
};

const refreshAll = async () => {
  isLoading.value = true;
  try {
    await loadFamilies();
    await loadSelectedFamily();
    if (canManageChildren.value) {
      await loadChildren();
      await loadChildWorkspace();
    } else {
      children.value = [];
      selectedChildId.value = null;
      await loadChildWorkspace();
    }
  } finally {
    isLoading.value = false;
  }
};

const handleCreateFamily = async () => {
  if (!familyForm.value.name.trim()) {
    ElMessage.warning('请输入家庭名称');
    return;
  }
  const res = await createFamily({ name: familyForm.value.name.trim() });
  selectedFamilyId.value = res.data.id;
  ElMessage.success('家庭组创建成功');
  await refreshAll();
};

const selectFamily = async (familyId: number) => {
  selectedFamilyId.value = familyId;
  await loadSelectedFamily();
};

const handleInvite = async () => {
  if (!selectedFamilyId.value) {
    ElMessage.warning('请先选择家庭组');
    return;
  }
  if (!inviteForm.value.inviteePhone.trim()) {
    ElMessage.warning('请输入被邀请人手机号');
    return;
  }
  await createFamilyInvitation(selectedFamilyId.value, {
    inviteePhone: inviteForm.value.inviteePhone.trim(),
    inviteeRole: inviteForm.value.inviteeRole
  });
  ElMessage.success(isCreator.value ? '邀请已发送至对方通知中心' : '邀请申请已发送给家庭创建者审批');
  inviteForm.value.inviteePhone = '';
  await loadSelectedFamily();
};

const handleCancelInvitation = async (invitationId: number) => {
  await cancelInvitation(invitationId);
  ElMessage.success('邀请已取消');
  await loadSelectedFamily();
};

const promoteToParent = async (member: FamilyMemberResponse) => {
  if (!selectedFamilyId.value) return;
  await updateFamilyMemberRole(selectedFamilyId.value, member.userId, 'parent');
  ElMessage.success('已设为家长');
  await loadSelectedFamily();
  if (canManageChildren.value) {
    await loadChildren();
  }
};

const removeMember = async (member: FamilyMemberResponse) => {
  if (!selectedFamilyId.value) return;
  await ElMessageBox.confirm(`确定要将 ${member.username || member.phone || member.userId} 移出家庭组吗？`, '移除成员', {
    confirmButtonText: '移除',
    cancelButtonText: '取消',
    type: 'warning'
  });
  await removeFamilyMember(selectedFamilyId.value, member.userId);
  ElMessage.success('成员已移除');
  await refreshAll();
};

const transferCreator = async (member: FamilyMemberResponse) => {
  if (!selectedFamilyId.value) return;
  await ElMessageBox.confirm(`确定将家庭创建者身份转让给 ${member.username || member.phone || member.userId} 吗？`, '转让创建者', {
    confirmButtonText: '转让',
    cancelButtonText: '取消',
    type: 'warning'
  });
  await transferFamilyCreator(selectedFamilyId.value, member.userId);
  ElMessage.success('家庭创建者已转让');
  await refreshAll();
};

const handleExpandFamily = async () => {
  if (!selectedFamilyId.value) return;
  await expandFamily(selectedFamilyId.value);
  ElMessage.success('家庭人数上限已扩容至10人');
  await loadSelectedFamily();
};

const handleDissolveFamily = async () => {
  if (!selectedFamilyId.value || !selectedFamily.value) return;
  await ElMessageBox.confirm(`确定解散「${selectedFamily.value.name}」吗？该操作会移除所有成员并取消待处理邀请。`, '解散家庭', {
    confirmButtonText: '解散',
    cancelButtonText: '取消',
    type: 'warning'
  });
  await dissolveFamily(selectedFamilyId.value);
  selectedFamilyId.value = null;
  ElMessage.success('家庭已解散');
  await refreshAll();
};

const handleGoalTypeChange = () => {
  goalForm.value.unit = getUnitByType(goalForm.value.type);
};

const createChildGoal = async () => {
  if (!selectedChildId.value) {
    ElMessage.warning('请先选择儿童');
    return;
  }
  await createGoal({ ...goalForm.value }, selectedChildId.value);
  ElMessage.success('儿童目标已创建');
  await loadChildWorkspace();
};

const removeChildGoal = async (goalId: number) => {
  if (!selectedChildId.value) return;
  await deleteGoal(goalId, selectedChildId.value);
  ElMessage.success('目标已删除');
  await loadChildWorkspace();
};

const createChildReminder = async () => {
  if (!selectedChildId.value) {
    ElMessage.warning('请先选择儿童');
    return;
  }
  if (!reminderForm.value.title.trim()) {
    ElMessage.warning('请输入提醒标题');
    return;
  }
  await createReminderRule({ ...reminderForm.value }, selectedChildId.value);
  reminderForm.value.title = '';
  reminderForm.value.message = '';
  ElMessage.success('儿童提醒已创建');
  await loadChildWorkspace();
};

const removeChildReminder = async (ruleId: number) => {
  if (!selectedChildId.value) return;
  await deleteReminderRule(ruleId, selectedChildId.value);
  ElMessage.success('提醒已删除');
  await loadChildWorkspace();
};

onMounted(refreshAll);
</script>

<template>
  <AppLayout title="家庭组" subtitle="家庭创建者管理成员，家长邀请成员并管理儿童健康目标与提醒">
    <div v-if="!families.length" class="mx-auto max-w-xl">
      <GlassCard class="p-6">
        <div class="flex items-center gap-3 mb-5">
          <Home class="w-5 h-5 text-[var(--ink-pine)]" />
          <h2 class="text-xl font-semibold text-gray-900">创建家庭</h2>
        </div>
        <div class="space-y-3">
          <input v-model="familyForm.name" class="bili-input w-full px-4 py-3" placeholder="家庭名称" />
          <button class="primary-button w-full py-3" @click="handleCreateFamily">
            创建家庭组
          </button>
        </div>
      </GlassCard>
    </div>

    <div v-else class="grid grid-cols-1 xl:grid-cols-3 gap-6">
      <div class="space-y-6">
        <GlassCard class="p-6">
          <div class="flex items-center gap-3 mb-5">
            <Home class="w-5 h-5 text-[var(--ink-pine)]" />
            <h2 class="text-xl font-semibold text-gray-900">我的家庭</h2>
          </div>

          <div class="space-y-2">
            <button
              v-for="family in families"
              :key="family.id"
              type="button"
              class="family-tab"
              :class="{ 'family-tab-active': selectedFamilyId === family.id }"
              @click="selectFamily(family.id)"
            >
              <span>{{ family.name }}</span>
              <span class="role-badge">{{ family.creator ? '创建者' : roleLabel(family.myRole) }}</span>
            </button>
          </div>
        </GlassCard>
      </div>

      <div class="xl:col-span-2 space-y-6">
        <GlassCard class="p-6">
          <div class="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <div class="flex items-center gap-3">
                <Users class="w-5 h-5 text-[var(--ink-pine)]" />
                <h2 class="text-xl font-semibold text-gray-900">
                  {{ selectedFamily?.name || '选择家庭组' }}
                </h2>
              </div>
              <p class="mt-1 text-sm text-gray-500">
                {{ selectedFamily ? `创建者：${selectedFamily.creatorUsername || selectedFamily.creatorUserId}` : '选择一个家庭组后可管理成员' }}
              </p>
            </div>
            <span v-if="selectedFamily" class="role-badge">
              我的身份：{{ selectedFamily.creator ? '创建者 / 家长' : roleLabel(selectedFamily.myRole) }}
            </span>
          </div>
          <div v-if="selectedFamily" class="mt-4 flex flex-wrap items-center gap-3">
            <span class="role-badge">
              成员 {{ selectedFamily.memberCount || members.length }} / {{ selectedFamily.maxMembers || 5 }}
            </span>
            <button
              v-if="canExpandFamily"
              class="secondary-action"
              @click="handleExpandFamily"
            >
              扩容至10人
            </button>
            <button
              v-if="isCreator"
              class="secondary-action danger-text"
              @click="handleDissolveFamily"
            >
              解散家庭
            </button>
          </div>

          <div v-if="canInvite" class="mt-6">
            <p v-if="!isCreator" class="mb-3 text-sm text-gray-500">
              家长发起邀请后，会先发送给家庭创建者审批；审批同意后，被邀请人会在通知中心收到邀请。
            </p>
            <div class="grid grid-cols-1 md:grid-cols-[1fr_160px_96px] gap-3">
              <input v-model="inviteForm.inviteePhone" class="bili-input px-4 py-3" placeholder="被邀请人手机号" />
              <el-select v-model="inviteForm.inviteeRole" class="bili-el-select" popper-class="bili-select-dropdown">
                <el-option label="作为儿童加入" value="child" />
                <el-option label="作为家长加入" value="parent" />
              </el-select>
              <button class="primary-button flex items-center justify-center gap-2" @click="handleInvite">
                <UserPlus class="w-4 h-4" />
                邀请
              </button>
            </div>
          </div>

          <div class="mt-6 grid grid-cols-1 gap-4" :class="{ 'lg:grid-cols-2': canInvite }">
            <div class="panel">
              <h3 class="panel-title">
                <Shield class="w-4 h-4" />
                成员
              </h3>
              <div class="space-y-3">
                <div v-for="member in members" :key="member.memberId" class="member-row">
                  <div>
                    <p class="font-semibold text-gray-900">
                      {{ member.username || member.phone || `用户 ${member.userId}` }}
                    </p>
                    <p class="text-sm text-gray-500">
                      {{ roleLabel(member.role) }}
                      <span v-if="member.creator"> · 创建者</span>
                      <span v-if="member.phone"> · {{ member.phone }}</span>
                    </p>
                  </div>
                  <div v-if="isCreator && !member.creator" class="flex gap-2">
                    <button
                      v-if="member.role === 'parent'"
                      class="icon-action"
                      title="设为创建者"
                      @click="transferCreator(member)"
                    >
                      <Crown class="w-4 h-4" />
                    </button>
                    <button
                      v-if="member.role === 'child'"
                      class="icon-action"
                      title="设为家长"
                      @click="promoteToParent(member)"
                    >
                      <Crown class="w-4 h-4" />
                    </button>
                    <button class="icon-action danger" title="移出家庭" @click="removeMember(member)">
                      <Trash2 class="w-4 h-4" />
                    </button>
                  </div>
                </div>
                <p v-if="!members.length" class="text-sm text-gray-500">暂无成员。</p>
              </div>
            </div>

            <div v-if="canInvite" class="panel">
              <h3 class="panel-title">
                <MailPlus class="w-4 h-4" />
                邀请记录
              </h3>
              <div class="space-y-3">
                <div v-for="item in pendingInvitations" :key="item.id" class="compact-row">
                  <div>
                    <p class="font-medium text-gray-900">{{ item.inviteePhone }}</p>
                    <p class="text-sm text-gray-500">
                      {{ roleLabel(item.inviteeRole) }} · {{ statusLabel(item.status) }} · {{ item.inviteCode }}
                    </p>
                  </div>
                  <button class="secondary-action" @click="handleCancelInvitation(item.id)">取消</button>
                </div>
                <p v-if="!pendingInvitations.length" class="text-sm text-gray-500">暂无待接受邀请。</p>
              </div>
            </div>
          </div>
        </GlassCard>

        <GlassCard v-if="canManageChildren" class="p-6">
          <div class="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <div class="flex items-center gap-3">
                <HeartPulse class="w-5 h-5 text-[var(--ink-pine)]" />
                <h2 class="text-xl font-semibold text-gray-900">儿童健康管理</h2>
              </div>
              <p class="mt-1 text-sm text-gray-500">家长可以查看家庭内儿童数据，并为儿童设置目标与提醒。</p>
            </div>
            <el-select
              v-model="selectedChildId"
              class="bili-el-select w-full lg:w-64"
              popper-class="bili-select-dropdown"
              placeholder="选择儿童"
              @change="loadChildWorkspace"
            >
              <el-option
                v-for="child in children"
                :key="child.userId"
                :label="`${child.username || child.phone || child.userId}（${child.familyName || '家庭'}）`"
                :value="child.userId"
              />
            </el-select>
          </div>

          <div v-if="selectedChild" class="mt-6 grid grid-cols-1 xl:grid-cols-3 gap-4">
            <div class="panel">
              <h3 class="panel-title">
                <HeartPulse class="w-4 h-4" />
                最近健康数据
              </h3>
              <div class="space-y-3">
                <div v-for="item in childHealthData" :key="item.id" class="metric-row">
                  <div>
                    <p class="font-semibold text-gray-900">{{ getLabelByType(item.type) }}</p>
                    <p class="text-sm text-gray-500">{{ formatDate(item.recordDate) }}</p>
                  </div>
                  <div class="text-right">
                    <p class="text-lg font-bold text-gray-900">{{ item.value }}</p>
                    <p class="text-xs text-gray-500">{{ item.unit }}</p>
                  </div>
                </div>
                <p v-if="!childHealthData.length" class="text-sm text-gray-500">暂无健康数据。</p>
              </div>
            </div>

            <div class="panel">
              <h3 class="panel-title">
                <Flag class="w-4 h-4" />
                目标
              </h3>
              <div class="space-y-3">
                <el-select v-model="goalForm.type" class="bili-el-select w-full" popper-class="bili-select-dropdown" @change="handleGoalTypeChange">
                  <el-option v-for="item in healthDataTypes" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
                <input v-model.number="goalForm.targetValue" type="number" class="bili-input w-full px-4 py-3" placeholder="目标值" />
                <el-select v-model="goalForm.period" class="bili-el-select w-full" popper-class="bili-select-dropdown">
                  <el-option label="每日" value="daily" />
                  <el-option label="每周" value="weekly" />
                  <el-option label="每月" value="monthly" />
                </el-select>
                <button class="primary-button w-full py-3" @click="createChildGoal">创建目标</button>
              </div>
              <div class="mt-5 space-y-3">
                <div v-for="goal in childGoals" :key="goal.id" class="compact-row">
                  <div>
                    <p class="font-medium text-gray-900">{{ getLabelByType(goal.type) }}</p>
                    <p class="text-sm text-gray-500">
                      {{ periodLabel(goal.period) }} · {{ goal.currentValue }}/{{ goal.targetValue }} {{ goal.unit }}
                    </p>
                  </div>
                  <button class="icon-action danger" title="删除目标" @click="removeChildGoal(goal.id)">
                    <Trash2 class="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>

            <div class="panel">
              <h3 class="panel-title">
                <Bell class="w-4 h-4" />
                提醒
              </h3>
              <div class="space-y-3">
                <input v-model="reminderForm.title" class="bili-input w-full px-4 py-3" placeholder="提醒标题" />
                <el-select v-model="reminderForm.frequency" class="bili-el-select w-full" popper-class="bili-select-dropdown">
                  <el-option label="每日" value="daily" />
                  <el-option label="每周" value="weekly" />
                  <el-option label="单次" value="once" />
                </el-select>
                <input v-model="reminderForm.remindTime" type="time" class="bili-input w-full px-4 py-3" />
                <input
                  v-if="reminderForm.frequency === 'once'"
                  v-model="reminderForm.remindDate"
                  type="date"
                  class="bili-input w-full px-4 py-3"
                />
                <textarea v-model="reminderForm.message" rows="2" class="bili-input w-full px-4 py-3" placeholder="提醒内容"></textarea>
                <button class="primary-button w-full py-3" @click="createChildReminder">创建提醒</button>
              </div>
              <div class="mt-5 space-y-3">
                <div v-for="rule in childRules" :key="rule.id" class="compact-row">
                  <div>
                    <p class="font-medium text-gray-900">{{ rule.title }}</p>
                    <p class="text-sm text-gray-500">{{ frequencyLabel(rule.frequency) }} · {{ rule.nextTriggerAt || '未安排' }}</p>
                  </div>
                  <button class="icon-action danger" title="删除提醒" @click="removeChildReminder(rule.id)">
                    <Trash2 class="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div v-else class="mt-6 rounded-lg border border-dashed border-[var(--ink-line)] p-8 text-center text-gray-500">
            暂无可管理儿童。家庭中的家长邀请儿童加入后，可在这里查看健康数据并设置目标与提醒。
          </div>
        </GlassCard>
      </div>
    </div>

    <div v-if="isLoading" class="fixed bottom-5 right-5 rounded-lg border border-[var(--ink-line)] bg-[#fffdf6] px-4 py-2 text-sm text-gray-600 shadow-lg">
      正在同步家庭数据...
    </div>
  </AppLayout>
</template>

<style scoped>
.family-tab,
.compact-row,
.member-row,
.metric-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.family-tab {
  width: 100%;
  min-height: 3rem;
  padding: 0.75rem 0.9rem;
  border: 1px solid rgba(121, 104, 78, 0.2);
  border-radius: 8px;
  background: rgba(255, 253, 246, 0.68);
  color: var(--ink-stone);
  text-align: left;
  transition: border-color 0.16s ease, background-color 0.16s ease;
}

.family-tab-active,
.family-tab:hover {
  border-color: rgba(59, 107, 87, 0.42);
  background: rgba(59, 107, 87, 0.09);
}

.panel {
  min-height: 12rem;
  border: 1px solid rgba(121, 104, 78, 0.18);
  border-radius: 8px;
  background: rgba(255, 253, 246, 0.58);
  padding: 1rem;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 1rem;
  color: var(--ink-stone);
  font-weight: 700;
}

.compact-row,
.member-row,
.metric-row {
  padding: 0.75rem;
  border: 1px solid rgba(121, 104, 78, 0.16);
  border-radius: 8px;
  background: rgba(255, 253, 246, 0.72);
}

.role-badge {
  display: inline-flex;
  align-items: center;
  min-height: 1.55rem;
  border-radius: 999px;
  background: rgba(59, 107, 87, 0.1);
  color: var(--ink-pine-dark);
  padding: 0.15rem 0.55rem;
  font-size: 0.75rem;
  font-weight: 700;
}

.mini-primary,
.secondary-action,
.icon-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  min-height: 2.25rem;
  border-radius: 8px;
  border: 1px solid rgba(121, 104, 78, 0.22);
  padding: 0.45rem 0.75rem;
  color: var(--ink-stone);
  background: rgba(255, 253, 246, 0.86);
  transition: border-color 0.16s ease, color 0.16s ease, background-color 0.16s ease;
}

.mini-primary {
  border-color: rgba(59, 107, 87, 0.34);
  background: rgba(59, 107, 87, 0.1);
  color: var(--ink-pine-dark);
}

.secondary-action:hover,
.icon-action:hover {
  border-color: rgba(59, 107, 87, 0.42);
  color: var(--ink-pine-dark);
  background: rgba(59, 107, 87, 0.09);
}

.danger-text {
  color: var(--ink-cinnabar);
}

.icon-action {
  width: 2.25rem;
  padding: 0;
}

.icon-action.danger:hover {
  border-color: rgba(163, 74, 58, 0.42);
  color: var(--ink-cinnabar);
  background: rgba(163, 74, 58, 0.08);
}
</style>
