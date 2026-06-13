<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import AppLayout from '../../components/common/AppLayout.vue';
import GlassCard from '../../components/common/GlassCard.vue';
import {
  createReminderRule,
  deleteNotification,
  deleteReminderRule,
  getNotifications,
  getReminderRules,
  markNotificationRead,
  toggleReminderRuleEnabled,
  updateReminderRule,
  type NotificationResponse,
  type ReminderRuleResponse
} from '../../api/reminders';
import {
  acceptInvitation,
  approveInvitation,
  getReceivedInvitations,
  rejectInvitation,
  rejectInvitationApproval,
  type FamilyInvitationResponse
} from '../../api/family';

const rules = ref<ReminderRuleResponse[]>([]);
const notifications = ref<NotificationResponse[]>([]);
const editingId = ref<number | null>(null);
const togglingId = ref<number | null>(null);
const browserPermission = ref<'unsupported' | NotificationPermission>('unsupported');
const form = ref({
  title: '',
  type: 'custom',
  message: '',
  frequency: 'daily' as 'daily' | 'weekly' | 'once',
  remindTime: '09:00',
  remindDate: '',
  weeklyDay: 1,
  enabled: true
});

const roleLabel = (role?: string) => role === 'parent' ? '家长' : '儿童';

const invitationToNotification = (invitation: FamilyInvitationResponse): NotificationResponse => ({
  id: -invitation.id,
  title: '家庭组邀请',
  type: 'family_invitation',
  message: `${invitation.inviterUsername || '家庭成员'} 邀请你以${roleLabel(invitation.inviteeRole)}身份加入「${invitation.familyName || '家庭组'}」。`,
  actionType: 'family_invitation',
  actionRefId: invitation.id,
  actionStatus: 'pending',
  status: 'unread',
  createdAt: invitation.createdAt || invitation.expiresAt
});

const mergeReceivedInvitations = (
  nextNotifications: NotificationResponse[],
  receivedInvitations: FamilyInvitationResponse[]
) => {
  const existingInvitationIds = new Set(
    nextNotifications
      .filter(item => item.actionType === 'family_invitation' && item.actionRefId)
      .map(item => item.actionRefId)
  );
  const fallbackNotifications = receivedInvitations
    .filter(item => item.status === 'pending' && !existingInvitationIds.has(item.id))
    .map(invitationToNotification);
  return [...fallbackNotifications, ...nextNotifications];
};

const syncNotifications = (nextNotifications: NotificationResponse[]) => {
  notifications.value = nextNotifications;
  window.dispatchEvent(new CustomEvent('global-notifications-updated', {
    detail: {
      notifications: nextNotifications,
      unreadCount: nextNotifications.filter(item => item.status !== 'read').length
    }
  }));
};

const loadData = async () => {
  const [rulesRes, notificationsRes, invitationsRes] = await Promise.all([
    getReminderRules(),
    getNotifications(),
    getReceivedInvitations()
  ]);
  rules.value = rulesRes.data || [];
  syncNotifications(mergeReceivedInvitations(notificationsRes.data || [], invitationsRes.data || []));
};

const resetForm = () => {
  editingId.value = null;
  form.value = {
    title: '',
    type: 'custom',
    message: '',
    frequency: 'daily',
    remindTime: '09:00',
    remindDate: '',
    weeklyDay: 1,
    enabled: true
  };
};

const submitRule = async () => {
  const payload = { ...form.value };
  if (editingId.value) {
    await updateReminderRule(editingId.value, payload);
    ElMessage.success('提醒规则更新成功');
  } else {
    await createReminderRule(payload);
    ElMessage.success('提醒规则创建成功');
  }
  resetForm();
  await loadData();
};

const editRule = (rule: ReminderRuleResponse) => {
  editingId.value = rule.id;
  form.value = {
    title: rule.title,
    type: rule.type || 'custom',
    message: rule.message || '',
    frequency: rule.frequency as 'daily' | 'weekly' | 'once',
    remindTime: rule.remindTime || '09:00',
    remindDate: rule.remindDate || '',
    weeklyDay: rule.weeklyDay || 1,
    enabled: rule.enabled
  };
};

const removeRule = async (id: number) => {
  await deleteReminderRule(id);
  ElMessage.success('提醒规则删除成功');
  await loadData();
};

const toggleRule = async (rule: ReminderRuleResponse) => {
  try {
    togglingId.value = rule.id;
    const nextEnabled = !rule.enabled;
    await toggleReminderRuleEnabled(rule.id, nextEnabled);
    ElMessage.success(nextEnabled ? '提醒已启用' : '提醒已停用');
    await loadData();
  } finally {
    togglingId.value = null;
  }
};

const markRead = async (id: number) => {
  if (id < 0) {
    return;
  }
  await markNotificationRead(id);
  await loadData();
};

const removeNotification = async (id: number) => {
  if (id < 0) {
    return;
  }
  await deleteNotification(id);
  await loadData();
};

const isPendingFamilyAction = (notification: NotificationResponse) => {
  return Boolean(notification.actionType && notification.actionRefId && notification.actionStatus === 'pending');
};

const handleFamilyAction = async (notification: NotificationResponse, action: 'accept' | 'reject' | 'approve' | 'approvalReject') => {
  if (!notification.actionRefId) return;
  if (action === 'accept') {
    await acceptInvitation(notification.actionRefId);
    ElMessage.success('已加入家庭组');
  } else if (action === 'reject') {
    await rejectInvitation(notification.actionRefId);
    ElMessage.success('已拒绝家庭邀请');
  } else if (action === 'approve') {
    await approveInvitation(notification.actionRefId);
    ElMessage.success('已同意邀请申请');
  } else {
    await rejectInvitationApproval(notification.actionRefId);
    ElMessage.success('已拒绝邀请申请');
  }
  await loadData();
};

const weekDayLabel = (day?: number) => ['-', '周一', '周二', '周三', '周四', '周五', '周六', '周日'][day || 0];

const requestBrowserPermission = async () => {
  if (typeof window === 'undefined' || !('Notification' in window)) {
    browserPermission.value = 'unsupported';
    return;
  }

  browserPermission.value = Notification.permission;
  if (Notification.permission === 'default') {
    const permission = await Notification.requestPermission();
    browserPermission.value = permission;
  }
};

const handleGlobalNotificationsUpdated = () => {
  loadData().catch(() => {
    // 静默刷新，避免全局通知事件影响当前操作
  });
};

onMounted(async () => {
  await requestBrowserPermission();
  await loadData();
  window.addEventListener('global-notifications-updated', handleGlobalNotificationsUpdated);
});

onBeforeUnmount(() => {
  window.removeEventListener('global-notifications-updated', handleGlobalNotificationsUpdated);
});
</script>

<template>
  <AppLayout title="提醒通知" subtitle="管理提醒规则并查看站内通知">
    <div class="grid grid-cols-1 xl:grid-cols-3 gap-6">
      <GlassCard class="p-6 xl:col-span-1">
        <h3 class="text-xl font-semibold text-gray-900 mb-5">{{ editingId ? '编辑提醒规则' : '新增提醒规则' }}</h3>
        <p class="text-sm text-gray-500 mb-4">
          当前提醒通过站内通知触发；当页面保持打开且已授权浏览器通知时，会自动弹出浏览器提醒。
        </p>
        <div class="space-y-4">
          <input v-model="form.title" class="bili-input w-full px-4 py-3 rounded-xl outline-none" placeholder="提醒标题" />
          <el-select v-model="form.type" class="bili-el-select w-full" popper-class="bili-select-dropdown">
            <el-option label="自定义" value="custom" />
            <el-option label="吃药" value="medication" />
            <el-option label="喝水" value="water" />
            <el-option label="睡眠" value="sleep" />
            <el-option label="复诊" value="followup" />
          </el-select>
          <el-select v-model="form.frequency" class="bili-el-select w-full" popper-class="bili-select-dropdown">
            <el-option label="每日" value="daily" />
            <el-option label="每周" value="weekly" />
            <el-option label="单次" value="once" />
          </el-select>
          <input v-model="form.remindTime" type="time" class="bili-input w-full px-4 py-3 rounded-xl outline-none" />
          <input
            v-if="form.frequency === 'once'"
            v-model="form.remindDate"
            type="date"
            class="bili-input w-full px-4 py-3 rounded-xl outline-none"
          />
          <el-select
            v-if="form.frequency === 'weekly'"
            v-model="form.weeklyDay"
            class="bili-el-select w-full"
            popper-class="bili-select-dropdown"
          >
            <el-option label="周一" :value="1" />
            <el-option label="周二" :value="2" />
            <el-option label="周三" :value="3" />
            <el-option label="周四" :value="4" />
            <el-option label="周五" :value="5" />
            <el-option label="周六" :value="6" />
            <el-option label="周日" :value="7" />
          </el-select>
          <textarea v-model="form.message" rows="3" class="bili-input w-full px-4 py-3 rounded-xl outline-none" placeholder="提醒内容"></textarea>
          <label class="flex items-center gap-3 text-gray-900 cursor-pointer group">
            <div class="relative">
              <input v-model="form.enabled" type="checkbox" class="sr-only" />
              <div
                class="w-11 h-6 rounded-full transition-colors duration-300 relative border"
                :class="form.enabled ? 'bg-pink-400 border-pink-300' : 'bg-#d1d5db/10 border-#d1d5db/10'"
              >
                <div
                  class="absolute top-1 w-4 h-4 rounded-full bg-#d1d5db transition-all duration-300 shadow-sm"
                  :class="form.enabled ? 'left-6' : 'left-1'"
                ></div>
              </div>
            </div>
            <span class="group-hover:text-pink-500 transition-colors">
              启用提醒
              <span class="ml-1 text-xs" :class="form.enabled ? 'text-emerald-300' : 'text-gray-500'">
                {{ form.enabled ? '已开启' : '已关闭' }}
              </span>
            </span>
          </label>
          <p class="text-xs text-gray-500">
            {{ form.enabled ? '创建后将立即参与提醒调度' : '创建后将保存为停用状态，不会生成提醒通知' }}
          </p>
          <div class="flex gap-3">
            <button class="primary-button flex-1" @click="submitRule">{{ editingId ? '保存修改' : '创建提醒' }}</button>
            <button class="secondary-button" @click="resetForm">重置</button>
          </div>
        </div>
      </GlassCard>

      <div class="xl:col-span-2 space-y-6">
        <GlassCard class="p-6">
          <h3 class="text-xl font-semibold text-gray-900 mb-4">提醒规则</h3>
          <div v-if="!rules.length" class="text-gray-500">暂无提醒规则</div>
          <div v-else class="space-y-4">
            <div v-for="rule in rules" :key="rule.id" class="card-row">
              <div>
                <div class="flex items-center gap-3 mb-2">
                  <h4 class="text-gray-900 font-semibold">{{ rule.title }}</h4>
                  <span class="badge">{{ rule.frequency }}</span>
                  <span v-if="rule.frequency === 'weekly'" class="badge">{{ weekDayLabel(rule.weeklyDay) }}</span>
                  <span class="badge" :class="rule.enabled ? 'badge-active' : 'badge-danger'">
                    {{ rule.enabled ? '已启用' : '已停用' }}
                  </span>
                </div>
                <p class="text-gray-500 text-sm">{{ rule.message || '请按时完成该提醒事项' }}</p>
                <p class="text-gray-500 text-sm mt-2">下次提醒：{{ rule.nextTriggerAt || '未安排' }}</p>
                <p v-if="rule.lastTriggeredAt" class="text-gray-500 text-sm mt-1">上次触发：{{ rule.lastTriggeredAt }}</p>
              </div>
              <div class="flex gap-3">
                <button
                  class="secondary-button"
                  :class="rule.enabled ? 'button-warning' : 'button-active'"
                  :disabled="togglingId === rule.id"
                  @click="toggleRule(rule)"
                >
                  {{ togglingId === rule.id ? '处理中...' : rule.enabled ? '停用提醒' : '启用提醒' }}
                </button>
                <button class="secondary-button" @click="editRule(rule)">编辑</button>
                <button class="danger-button" @click="removeRule(rule.id)">删除</button>
              </div>
            </div>
          </div>
        </GlassCard>

        <GlassCard class="p-6">
          <h3 class="text-xl font-semibold text-gray-900 mb-4">通知中心</h3>
          <p class="text-sm text-gray-500 mb-4">
            {{
              browserPermission === 'granted'
                ? '浏览器通知已开启，新的站内提醒会自动弹出。'
                : browserPermission === 'denied'
                  ? '浏览器通知已被禁止，目前仅在本页通知中心显示提醒。'
                  : '当前浏览器不支持系统通知，提醒仅在本页通知中心显示。'
            }}
          </p>
          <div v-if="!notifications.length" class="text-gray-500">当前没有通知</div>
          <div v-else class="space-y-4">
            <div v-for="notification in notifications" :key="notification.id" class="card-row">
              <div>
                <div class="flex items-center gap-3 mb-2">
                  <h4 class="text-gray-900 font-semibold">{{ notification.title }}</h4>
                  <span class="badge" :class="notification.status === 'read' ? 'badge-muted' : ''">
                    {{ notification.status === 'read' ? '已读' : '未读' }}
                  </span>
                </div>
                <p class="text-gray-500 text-sm">{{ notification.message }}</p>
                <p class="text-gray-500 text-sm mt-2">提醒时间：{{ notification.scheduledFor || notification.createdAt }}</p>
              </div>
              <div class="flex gap-3">
                <template v-if="isPendingFamilyAction(notification)">
                  <button
                    v-if="notification.actionType === 'family_invitation'"
                    class="secondary-button button-active"
                    @click="handleFamilyAction(notification, 'accept')"
                  >
                    接受
                  </button>
                  <button
                    v-if="notification.actionType === 'family_invitation'"
                    class="danger-button"
                    @click="handleFamilyAction(notification, 'reject')"
                  >
                    拒绝
                  </button>
                  <button
                    v-if="notification.actionType === 'family_invitation_approval'"
                    class="secondary-button button-active"
                    @click="handleFamilyAction(notification, 'approve')"
                  >
                    同意
                  </button>
                  <button
                    v-if="notification.actionType === 'family_invitation_approval'"
                    class="danger-button"
                    @click="handleFamilyAction(notification, 'approvalReject')"
                  >
                    拒绝
                  </button>
                </template>
                <button
                  v-if="notification.id > 0 && notification.status !== 'read'"
                  class="secondary-button"
                  @click="markRead(notification.id)"
                >
                  标记已读
                </button>
                <button v-if="notification.id > 0" class="danger-button" @click="removeNotification(notification.id)">删除</button>
              </div>
            </div>
          </div>
        </GlassCard>
      </div>
    </div>
  </AppLayout>
</template>

<style scoped>
.primary-button,
.secondary-button,
.danger-button {
  padding: 0.8rem 1rem;
  border-radius: 0.9rem;
  border: 1px solid #E0E0E8;
  color: #374151;
  font-weight: 500;
}

.primary-button {
  background: linear-gradient(135deg, #FB7299, #FF8DB0);
  color: white;
}

.secondary-button {
  background: #F4F5F7;
}

.danger-button {
  background: rgba(239, 68, 68, 0.08);
  color: #EF4444;
}

.card-row {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem 0;
  border-bottom: 1px solid #F0F0F5;
}

.card-row:last-child {
  border-bottom: none;
}

.badge {
  padding: 0.2rem 0.6rem;
  border-radius: 999px;
  font-size: 0.75rem;
  background: #F4F5F7;
  color: #374151;
}

.badge-danger {
  background: rgba(239, 68, 68, 0.1);
  color: #DC2626;
}

.badge-active {
  background: rgba(16, 185, 129, 0.1);
  color: #059669;
}

.badge-muted {
  background: rgba(107, 114, 128, 0.1);
  color: #6B7280;
}

.button-active {
  background: rgba(16, 185, 129, 0.1);
  color: #059669;
}

.button-warning {
  background: rgba(245, 158, 11, 0.1);
  color: #D97706;
}
</style>
