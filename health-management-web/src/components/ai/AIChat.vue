<script setup lang="ts">
import { nextTick, ref, watch } from 'vue';
import {
  AlertCircle,
  BellRing,
  Bot,
  CheckCircle2,
  Clock,
  Loader2,
  Send,
  Target,
  User
} from 'lucide-vue-next';
import { ElMessage } from 'element-plus';
import {
  generateAIActionDraft,
  getAIStreamResponse,
  type AIActionDraftResponse,
  type AIChatMessage,
  type AIGoalDraft,
  type AIReminderDraft
} from '../../api/ai';
import { createGoal } from '../../api/goals';
import { createReminderRule } from '../../api/reminders';
import { healthMetrics } from '../../config/healthMetrics';
import { formatTime } from '../../lib/utils';

type LocalChatMessage = AIChatMessage & {
  actionDraft?: AIActionDraftResponse;
  actionDraftLoading?: boolean;
  actionDraftError?: string;
  goalCreated?: boolean;
  reminderCreated?: boolean;
};

const props = defineProps<{
  chatId: string;
  messages?: AIChatMessage[];
}>();

const emit = defineEmits<{
  sent: [];
  refreshSessions: [];
}>();

const messages = ref<LocalChatMessage[]>([]);
const inputMessage = ref('');
const loading = ref(false);
const streamingMessageId = ref<string | null>(null);
const creatingGoalMessageId = ref<string | null>(null);
const creatingReminderMessageId = ref<string | null>(null);
const scrollRef = ref<HTMLElement | null>(null);

const periodOptions = [
  { value: 'daily', label: '每日' },
  { value: 'weekly', label: '每周' },
  { value: 'monthly', label: '每月' }
] as const;

const frequencyOptions = [
  { value: 'daily', label: '每日' },
  { value: 'weekly', label: '每周' },
  { value: 'once', label: '单次' }
] as const;

const weekdayOptions = [
  { value: 1, label: '周一' },
  { value: 2, label: '周二' },
  { value: 3, label: '周三' },
  { value: 4, label: '周四' },
  { value: 5, label: '周五' },
  { value: 6, label: '周六' },
  { value: 7, label: '周日' }
] as const;

const actionMetricOptions = [
  ...healthMetrics.map(metric => ({ type: metric.type, label: metric.label })),
  { type: 'exercise', label: '运动' },
  { type: 'diet', label: '饮食' },
  { type: 'mood', label: '情绪' }
];

const actionVerbKeywords = ['创建', '设置', '设定', '安排', '生成', '制定', '添加', '新增', '建立', '帮我', '提醒我', '记得', '定一个', '设一个'];
const actionTargetKeywords = ['目标', '提醒', '通知', '闹钟'];

const defaultWelcome = [
  '我会读取你的健康数据、目标、提醒和设备同步情况来回答。',
  '如果你想让我帮你做事，可以直接说：帮我创建每天8000步目标，或提醒我每天晚上9点记录体重。',
  '我生成的目标和提醒都会先显示草案，你确认后才会创建。'
];

watch(
  () => props.messages,
  value => {
    messages.value = (value || []).map(item => ({ ...item }));
    nextTick(scrollToBottom);
  },
  { immediate: true }
);

watch(
  () => props.chatId,
  () => {
    inputMessage.value = '';
    streamingMessageId.value = null;
  }
);

const cleanAssistantText = (content: string) => {
  return (content || '')
    .split('\n')
    .map(line => line.trim()
      .replace(/^#{1,6}\s*/, '')
      .replace(/^[-*_]{3,}$/, '')
      .replace(/\*\*/g, '')
      .replace(/__/g, '')
      .replace(/`/g, '')
      .replace(/^[-*]\s+/, '• '))
    .filter((line, index, arr) => line || arr[index - 1])
    .join('\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
};

function scrollToBottom() {
  if (scrollRef.value) {
    scrollRef.value.scrollTop = scrollRef.value.scrollHeight;
  }
}

const buildConversationContext = (): AIChatMessage[] => {
  return messages.value.slice(-6).map(message => ({
    id: message.id,
    role: message.role,
    content: message.content,
    timestamp: message.timestamp,
    chatId: message.chatId
  }));
};

const shouldGenerateActionDraft = (message: string) => {
  const normalized = message.trim().toLowerCase();
  if (!normalized) return false;
  const hasVerb = actionVerbKeywords.some(keyword => normalized.includes(keyword));
  const hasTarget = actionTargetKeywords.some(keyword => normalized.includes(keyword));
  const hasReminderPhrase = normalized.includes('提醒我') || normalized.includes('记得');
  const hasTimeReminder = normalized.includes('提醒') && /(\d{1,2}(:|点)|今天|明天|后天)/.test(normalized);
  return (hasVerb && hasTarget) || hasReminderPhrase || hasTimeReminder;
};

const updateMessage = (messageId: string, updater: (message: LocalChatMessage) => void) => {
  const index = messages.value.findIndex(message => message.id === messageId);
  if (index === -1) return;
  const nextMessage = { ...messages.value[index] };
  updater(nextMessage);
  messages.value.splice(index, 1, nextMessage);
};

const attachActionDraft = async (instruction: string, aiMessageId: string) => {
  if (!shouldGenerateActionDraft(instruction)) return;

  updateMessage(aiMessageId, message => {
    message.actionDraftLoading = true;
    message.actionDraftError = undefined;
  });

  try {
    const draft = await generateAIActionDraft(instruction);
    if (draft.goalDraft || draft.reminderDraft || draft.warnings.length > 0) {
      updateMessage(aiMessageId, message => {
        message.actionDraft = draft;
      });
    }
  } catch (error: any) {
    updateMessage(aiMessageId, message => {
      message.actionDraftError = error?.response?.data?.message || error?.message || '草案生成失败';
    });
  } finally {
    updateMessage(aiMessageId, message => {
      message.actionDraftLoading = false;
    });
  }
};

const buildGoalPayload = (draft?: AIGoalDraft) => {
  if (!draft) return null;
  return {
    type: draft.type,
    targetValue: Number(draft.targetValue),
    unit: draft.unit || undefined,
    period: draft.period,
    enabled: draft.enabled ?? true
  };
};

const buildReminderPayload = (draft?: AIReminderDraft) => {
  if (!draft) return null;
  return {
    title: draft.title,
    type: draft.type || undefined,
    message: draft.message || undefined,
    frequency: draft.frequency,
    remindTime: draft.remindTime || undefined,
    remindDate: draft.frequency === 'once' ? (draft.remindDate || undefined) : undefined,
    weeklyDay: draft.frequency === 'weekly' ? draft.weeklyDay : undefined,
    enabled: draft.enabled ?? true
  };
};

const createGoalFromDraft = async (message: LocalChatMessage) => {
  const payload = buildGoalPayload(message.actionDraft?.goalDraft);
  if (!payload || message.goalCreated) return;
  creatingGoalMessageId.value = message.id;
  try {
    await createGoal(payload);
    updateMessage(message.id, item => { item.goalCreated = true; });
    ElMessage.success('目标已创建');
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || '创建目标失败');
  } finally {
    creatingGoalMessageId.value = null;
  }
};

const createReminderFromDraft = async (message: LocalChatMessage) => {
  const payload = buildReminderPayload(message.actionDraft?.reminderDraft);
  if (!payload || message.reminderCreated) return;
  creatingReminderMessageId.value = message.id;
  try {
    await createReminderRule(payload);
    updateMessage(message.id, item => { item.reminderCreated = true; });
    ElMessage.success('提醒已创建');
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || '创建提醒失败');
  } finally {
    creatingReminderMessageId.value = null;
  }
};

const sendMessage = async () => {
  if (!inputMessage.value.trim() || loading.value) return;
  const message = inputMessage.value.trim();
  inputMessage.value = '';

  const userMessage: LocalChatMessage = {
    id: `local_user_${Date.now()}`,
    role: 'user',
    content: message,
    timestamp: new Date().toISOString(),
    chatId: props.chatId
  };
  messages.value.push(userMessage);
  loading.value = true;

  const aiMessageId = `local_ai_${Date.now() + 1}`;
  streamingMessageId.value = aiMessageId;
  const aiMessage: LocalChatMessage = {
    id: aiMessageId,
    role: 'assistant',
    content: '',
    timestamp: new Date().toISOString(),
    chatId: props.chatId
  };
  messages.value.push(aiMessage);
  await nextTick(scrollToBottom);

  try {
    let fullContent = '';
    await getAIStreamResponse({
      message,
      chatId: props.chatId,
      context: buildConversationContext()
    }, async chunk => {
      fullContent += chunk;
      updateMessage(aiMessageId, item => {
        item.content = cleanAssistantText(fullContent);
      });
      await nextTick(scrollToBottom);
    });

    updateMessage(aiMessageId, item => {
      item.content = cleanAssistantText(fullContent);
    });
    await attachActionDraft(message, aiMessageId);
    emit('sent');
    emit('refreshSessions');
  } catch (error) {
    updateMessage(aiMessageId, item => {
      item.content = '连接助手时遇到问题，请稍后重试。';
    });
  } finally {
    loading.value = false;
    streamingMessageId.value = null;
    await nextTick(scrollToBottom);
  }
};

const handleKeyDown = (e: KeyboardEvent) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    sendMessage();
  }
};

const handleExternalQuestion = async (question: string) => {
  inputMessage.value = question;
  await nextTick();
  await sendMessage();
};

defineExpose({ handleExternalQuestion });
</script>

<template>
  <section class="ai-chat-shell">
    <div ref="scrollRef" class="message-scroll">
      <div v-if="messages.length === 0" class="welcome-panel">
        <div class="assistant-mark">
          <Bot class="h-6 w-6" />
        </div>
        <h2>今天想从哪里开始？</h2>
        <p v-for="line in defaultWelcome" :key="line">{{ line }}</p>
      </div>

      <div
        v-for="message in messages"
        :key="message.id"
        class="message-row"
        :class="message.role === 'user' ? 'is-user' : 'is-assistant'"
      >
        <div class="avatar">
          <User v-if="message.role === 'user'" class="h-4 w-4" />
          <Bot v-else class="h-4 w-4" />
        </div>
        <article class="message-card">
          <div v-if="message.role === 'user'" class="message-text">{{ message.content }}</div>
          <div v-else class="message-text assistant-text">{{ cleanAssistantText(message.content) }}</div>

          <div v-if="message.role === 'assistant' && message.actionDraftLoading" class="draft-status">
            <Loader2 class="h-4 w-4 animate-spin" />
            正在整理可执行草案
          </div>

          <div v-if="message.role === 'assistant' && message.actionDraftError" class="draft-error">
            <AlertCircle class="h-4 w-4" />
            {{ message.actionDraftError }}
          </div>

          <div v-if="message.role === 'assistant' && message.actionDraft" class="draft-stack">
            <div class="draft-summary">
              {{ message.actionDraft.summary }}
            </div>

            <div v-if="message.actionDraft.warnings.length" class="draft-warning">
              <AlertCircle class="h-4 w-4" />
              <div>
                <p v-for="warning in message.actionDraft.warnings" :key="warning">{{ warning }}</p>
              </div>
            </div>

            <div v-if="message.actionDraft.goalDraft" class="draft-card">
              <header>
                <span><Target class="h-4 w-4" />目标草案</span>
                <button
                  @click="createGoalFromDraft(message)"
                  :disabled="creatingGoalMessageId === message.id || message.goalCreated"
                >
                  <CheckCircle2 v-if="message.goalCreated" class="h-4 w-4" />
                  <Loader2 v-else-if="creatingGoalMessageId === message.id" class="h-4 w-4 animate-spin" />
                  {{ message.goalCreated ? '已创建' : creatingGoalMessageId === message.id ? '创建中' : '创建目标' }}
                </button>
              </header>
              <div class="draft-grid">
                <label>
                  指标
                  <select v-model="message.actionDraft.goalDraft.type">
                    <option v-for="metric in actionMetricOptions" :key="metric.type" :value="metric.type">{{ metric.label }}</option>
                  </select>
                </label>
                <label>
                  目标值
                  <input v-model.number="message.actionDraft.goalDraft.targetValue" type="number" min="1" step="0.1" />
                </label>
                <label>
                  单位
                  <input v-model="message.actionDraft.goalDraft.unit" type="text" />
                </label>
                <label>
                  周期
                  <select v-model="message.actionDraft.goalDraft.period">
                    <option v-for="item in periodOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
                  </select>
                </label>
              </div>
              <p>当前参考值：{{ message.actionDraft.goalDraft.currentValue ?? 0 }} {{ message.actionDraft.goalDraft.unit || '' }}</p>
              <p>{{ message.actionDraft.goalDraft.suggestionReason }}</p>
            </div>

            <div v-if="message.actionDraft.reminderDraft" class="draft-card">
              <header>
                <span><BellRing class="h-4 w-4" />提醒草案</span>
                <button
                  @click="createReminderFromDraft(message)"
                  :disabled="creatingReminderMessageId === message.id || message.reminderCreated"
                >
                  <CheckCircle2 v-if="message.reminderCreated" class="h-4 w-4" />
                  <Loader2 v-else-if="creatingReminderMessageId === message.id" class="h-4 w-4 animate-spin" />
                  {{ message.reminderCreated ? '已创建' : creatingReminderMessageId === message.id ? '创建中' : '创建提醒' }}
                </button>
              </header>
              <div class="draft-grid">
                <label class="span-2">
                  标题
                  <input v-model="message.actionDraft.reminderDraft.title" type="text" />
                </label>
                <label>
                  频率
                  <select v-model="message.actionDraft.reminderDraft.frequency">
                    <option v-for="item in frequencyOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
                  </select>
                </label>
                <label>
                  时间
                  <input v-model="message.actionDraft.reminderDraft.remindTime" type="time" />
                </label>
                <label v-if="message.actionDraft.reminderDraft.frequency === 'weekly'">
                  星期
                  <select v-model="message.actionDraft.reminderDraft.weeklyDay">
                    <option v-for="item in weekdayOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
                  </select>
                </label>
                <label v-if="message.actionDraft.reminderDraft.frequency === 'once'">
                  日期
                  <input v-model="message.actionDraft.reminderDraft.remindDate" type="date" />
                </label>
                <label class="span-2">
                  内容
                  <textarea v-model="message.actionDraft.reminderDraft.message" rows="3"></textarea>
                </label>
              </div>
              <p>{{ message.actionDraft.reminderDraft.suggestionReason }}</p>
            </div>
          </div>

          <footer>
            <Clock class="h-3.5 w-3.5" />
            {{ formatTime(message.timestamp) }}
            <Loader2 v-if="streamingMessageId === message.id" class="h-3.5 w-3.5 animate-spin" />
          </footer>
        </article>
      </div>
    </div>

    <div class="composer">
      <textarea
        v-model="inputMessage"
        @keydown="handleKeyDown"
        placeholder="输入健康问题，或直接说：帮我创建目标、提醒我记录数据"
        :disabled="loading"
      />
      <button @click="sendMessage" :disabled="loading || !inputMessage.trim()" title="发送">
        <Loader2 v-if="loading" class="h-5 w-5 animate-spin" />
        <Send v-else class="h-5 w-5" />
      </button>
    </div>
  </section>
</template>

<style scoped>
.ai-chat-shell {
  min-height: 72vh;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(121, 104, 78, 0.22);
  border-radius: 8px;
  background: rgba(255, 253, 246, 0.94);
  overflow: hidden;
}

.message-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 1.25rem;
}

.welcome-panel {
  margin: 3rem auto;
  max-width: 34rem;
  text-align: center;
  color: var(--ink-muted);
}

.assistant-mark {
  width: 3.2rem;
  height: 3.2rem;
  margin: 0 auto 1rem;
  display: grid;
  place-items: center;
  border: 1px solid rgba(59, 107, 87, 0.28);
  border-radius: 8px;
  color: var(--ink-pine-dark);
  background: rgba(59, 107, 87, 0.08);
}

.welcome-panel h2 {
  margin-bottom: 0.75rem;
  color: var(--ink-stone);
  font-size: 1.35rem;
  font-weight: 700;
}

.welcome-panel p {
  margin-top: 0.45rem;
  line-height: 1.8;
}

.message-row {
  display: flex;
  gap: 0.7rem;
  margin-bottom: 1rem;
}

.message-row.is-user {
  flex-direction: row-reverse;
}

.avatar {
  width: 2rem;
  height: 2rem;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border: 1px solid rgba(121, 104, 78, 0.22);
  border-radius: 8px;
  color: var(--ink-pine-dark);
  background: rgba(247, 243, 232, 0.72);
}

.is-user .avatar {
  color: #fffdf6;
  background: var(--ink-pine);
}

.message-card {
  width: min(100%, 46rem);
  border: 1px solid rgba(121, 104, 78, 0.18);
  border-radius: 8px;
  background: rgba(247, 243, 232, 0.42);
  padding: 0.85rem 1rem;
  color: var(--ink-stone);
}

.is-user .message-card {
  max-width: 34rem;
  background: rgba(59, 107, 87, 0.1);
}

.message-text {
  white-space: pre-wrap;
  line-height: 1.8;
  overflow-wrap: anywhere;
}

.assistant-text {
  color: #34302c;
}

.message-card footer {
  margin-top: 0.55rem;
  display: flex;
  align-items: center;
  gap: 0.35rem;
  color: var(--ink-light);
  font-size: 0.78rem;
}

.draft-status,
.draft-error,
.draft-warning,
.draft-summary {
  margin-top: 0.85rem;
  border-radius: 8px;
  padding: 0.7rem 0.8rem;
  font-size: 0.9rem;
}

.draft-status,
.draft-summary {
  display: flex;
  gap: 0.45rem;
  color: var(--ink-pine-dark);
  background: rgba(59, 107, 87, 0.08);
}

.draft-error,
.draft-warning {
  display: flex;
  gap: 0.5rem;
  color: var(--ink-cinnabar);
  background: rgba(163, 74, 58, 0.08);
}

.draft-stack {
  margin-top: 0.85rem;
  display: grid;
  gap: 0.8rem;
}

.draft-card {
  border: 1px solid rgba(121, 104, 78, 0.2);
  border-radius: 8px;
  background: rgba(255, 253, 246, 0.82);
  padding: 0.9rem;
}

.draft-card header {
  margin-bottom: 0.8rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.draft-card header span,
.draft-card header button {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}

.draft-card header span {
  color: var(--ink-stone);
  font-weight: 700;
}

.draft-card header button {
  border-radius: 8px;
  background: var(--ink-pine);
  color: #fffdf6;
  padding: 0.5rem 0.75rem;
  font-size: 0.85rem;
  font-weight: 700;
}

.draft-card header button:disabled {
  opacity: 0.62;
  cursor: not-allowed;
}

.draft-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.75rem;
}

.draft-grid label {
  color: var(--ink-muted);
  font-size: 0.84rem;
}

.draft-grid .span-2 {
  grid-column: 1 / -1;
}

.draft-grid input,
.draft-grid select,
.draft-grid textarea {
  margin-top: 0.35rem;
  width: 100%;
  border: 1px solid rgba(121, 104, 78, 0.24);
  border-radius: 8px;
  background: rgba(255, 253, 246, 0.94);
  padding: 0.58rem 0.7rem;
  color: var(--ink-stone);
  outline: none;
}

.draft-card p {
  margin-top: 0.65rem;
  color: var(--ink-muted);
  font-size: 0.88rem;
  line-height: 1.7;
}

.composer {
  border-top: 1px solid rgba(121, 104, 78, 0.18);
  background: rgba(247, 243, 232, 0.5);
  padding: 0.9rem;
  display: flex;
  gap: 0.75rem;
  align-items: flex-end;
}

.composer textarea {
  min-height: 4.6rem;
  max-height: 10rem;
  flex: 1;
  resize: vertical;
  border: 1px solid rgba(121, 104, 78, 0.24);
  border-radius: 8px;
  background: rgba(255, 253, 246, 0.94);
  padding: 0.85rem;
  color: var(--ink-stone);
  outline: none;
  line-height: 1.6;
}

.composer textarea:focus {
  border-color: var(--ink-pine);
  box-shadow: 0 0 0 3px rgba(59, 107, 87, 0.1);
}

.composer button {
  width: 2.75rem;
  height: 2.75rem;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: var(--ink-pine);
  color: #fffdf6;
}

.composer button:disabled {
  opacity: 0.48;
  cursor: not-allowed;
}

@media (max-width: 720px) {
  .message-scroll {
    padding: 0.9rem;
  }

  .draft-grid {
    grid-template-columns: 1fr;
  }

  .message-card {
    width: 100%;
  }
}
</style>
