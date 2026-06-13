<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import {
  Activity,
  Bell,
  Bot,
  Check,
  ChevronDown,
  HelpCircle,
  MessageSquarePlus,
  RefreshCw,
  Sparkles,
  Target,
  Trash2
} from 'lucide-vue-next';
import { ElMessage, ElMessageBox } from 'element-plus';
import AppLayout from '../../components/common/AppLayout.vue';
import AIChatPanel from '../../components/ai/AIChat.vue';
import type AIChatPanelComponent from '../../components/ai/AIChat.vue';
import {
  clearChatHistory,
  getAvailableProviders,
  getChatHistory,
  getChatSessions,
  getCurrentProvider,
  getRecommendedQuestions,
  switchProvider,
  type AIChatMessage,
  type AIChatSession,
  type AIProviderInfo,
  type AIRecommendedQuestion
} from '../../api/ai';

const aiChatRef = ref<InstanceType<typeof AIChatPanelComponent> | null>(null);
const sessions = ref<AIChatSession[]>([]);
const activeChatId = ref(`chat_${Date.now()}`);
const activeMessages = ref<AIChatMessage[]>([]);
const recommendedQuestions = ref<AIRecommendedQuestion[]>([]);
const currentProvider = ref<AIProviderInfo | null>(null);
const availableProviders = ref<AIProviderInfo[]>([]);
const showProviderDropdown = ref(false);
const switchingProvider = ref(false);
const loadingSessions = ref(false);
const loadingMessages = ref(false);

const featureCards = [
  {
    icon: Activity,
    title: '健康分析',
    text: '读取最近健康数据，解释趋势和异常',
    prompt: '请分析我最近的健康数据，有哪些需要注意？'
  },
  {
    icon: Target,
    title: '目标规划',
    text: '按数据生成目标草案，确认后创建',
    prompt: '帮我创建一个每天8000步的目标'
  },
  {
    icon: Bell,
    title: '提醒设置',
    text: '生成提醒草案，到点进入站内通知',
    prompt: '提醒我每天晚上9点记录体重'
  },
  {
    icon: HelpCircle,
    title: '异常解读',
    text: '解释异常值、复测建议和就医时机',
    prompt: '我的异常健康指标应该怎么处理？'
  }
];

const providerLabel = computed(() => currentProvider.value?.name || '模拟模式');

const isMock = computed(() => currentProvider.value?.code === 'mock');

const loadProviderInfo = async () => {
  try {
    currentProvider.value = await getCurrentProvider();
  } catch {
    currentProvider.value = { code: 'mock', name: '模拟模式' };
  }
};

const loadAvailableProviders = async () => {
  try {
    availableProviders.value = await getAvailableProviders();
  } catch {
    availableProviders.value = [{ code: 'mock', name: '模拟模式' }];
  }
};

const loadSessions = async () => {
  loadingSessions.value = true;
  try {
    sessions.value = await getChatSessions();
  } catch {
    sessions.value = [];
  } finally {
    loadingSessions.value = false;
  }
};

const loadRecommendedQuestions = async () => {
  try {
    recommendedQuestions.value = await getRecommendedQuestions();
  } catch {
    recommendedQuestions.value = [];
  }
};

const loadMessages = async (chatId: string) => {
  loadingMessages.value = true;
  try {
    activeMessages.value = await getChatHistory(chatId);
  } catch {
    activeMessages.value = [];
  } finally {
    loadingMessages.value = false;
  }
};

const createNewChat = () => {
  activeChatId.value = `chat_${Date.now()}`;
  activeMessages.value = [];
};

const switchSession = async (chatId: string) => {
  activeChatId.value = chatId;
  await loadMessages(chatId);
};

const deleteSession = async (session: AIChatSession) => {
  try {
    await ElMessageBox.confirm(`删除对话“${session.title}”？`, '删除对话', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    });
    await clearChatHistory(session.chatId);
    if (activeChatId.value === session.chatId) {
      createNewChat();
    }
    await loadSessions();
    ElMessage.success('对话已删除');
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败');
    }
  }
};

const clearAllSessions = async () => {
  if (!sessions.value.length) return;
  try {
    await ElMessageBox.confirm('清空所有 AI 对话记录？', '清空对话', {
      confirmButtonText: '清空',
      cancelButtonText: '取消',
      type: 'warning'
    });
    await clearChatHistory();
    createNewChat();
    await loadSessions();
    ElMessage.success('已清空对话');
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('清空失败');
    }
  }
};

const handleSwitchProvider = async (providerCode: string) => {
  if (switchingProvider.value) return;
  switchingProvider.value = true;
  try {
    const result = await switchProvider(providerCode);
    if (result.success) {
      await Promise.all([loadProviderInfo(), loadAvailableProviders()]);
      ElMessage.success(result.message);
    } else {
      ElMessage.warning(result.error || '切换失败');
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || error.message || '切换失败');
  } finally {
    switchingProvider.value = false;
    showProviderDropdown.value = false;
  }
};

const sendPrompt = async (prompt: string) => {
  await aiChatRef.value?.handleExternalQuestion(prompt);
};

const handleSent = async () => {
  await Promise.all([loadSessions(), loadRecommendedQuestions()]);
};

const refreshAll = async () => {
  await Promise.all([
    loadSessions(),
    loadMessages(activeChatId.value),
    loadRecommendedQuestions(),
    loadProviderInfo(),
    loadAvailableProviders()
  ]);
};

onMounted(refreshAll);
</script>

<template>
  <AppLayout title="智能健康助手" subtitle="分析健康数据，生成目标和提醒草案">
    <div class="ai-workbench">
      <aside class="ai-sidebar">
        <div class="sidebar-card">
          <button class="new-chat-button" @click="createNewChat">
            <MessageSquarePlus class="h-4 w-4" />
            新建对话
          </button>

          <div class="provider-block">
            <button class="provider-button" @click.stop="showProviderDropdown = !showProviderDropdown">
              <span :class="['provider-dot', isMock ? 'mock' : 'live']"></span>
              {{ providerLabel }}
              <ChevronDown class="h-4 w-4" :class="{ rotate: showProviderDropdown }" />
            </button>
            <div v-if="showProviderDropdown" class="provider-menu">
              <button
                v-for="provider in availableProviders"
                :key="provider.code"
                @click="handleSwitchProvider(provider.code)"
                :disabled="switchingProvider"
              >
                <Sparkles class="h-4 w-4" />
                <span>{{ provider.name }}</span>
                <Check v-if="currentProvider?.code === provider.code" class="ml-auto h-4 w-4" />
              </button>
            </div>
          </div>
        </div>

        <section class="sidebar-card">
          <div class="section-heading">
            <span>推荐问题</span>
            <button @click="loadRecommendedQuestions" title="刷新推荐问题">
              <RefreshCw class="h-4 w-4" />
            </button>
          </div>
          <div class="question-list">
            <button
              v-for="item in recommendedQuestions"
              :key="item.question"
              @click="sendPrompt(item.question)"
            >
              <span>{{ item.category }}</span>
              <strong>{{ item.question }}</strong>
              <small>{{ item.reason }}</small>
            </button>
          </div>
        </section>

        <section class="sidebar-card session-card">
          <div class="section-heading">
            <span>对话</span>
            <button @click="clearAllSessions" title="清空全部对话">
              <Trash2 class="h-4 w-4" />
            </button>
          </div>
          <div v-if="loadingSessions" class="loading-line">
            <RefreshCw class="h-4 w-4 animate-spin" />
            加载中
          </div>
          <div v-else-if="!sessions.length" class="empty-line">暂无历史对话</div>
          <div v-else class="session-list">
            <div
              v-for="session in sessions"
              :key="session.chatId"
              class="session-item"
              :class="{ active: session.chatId === activeChatId }"
            >
              <button class="session-main" @click="switchSession(session.chatId)">
                <strong>{{ session.title }}</strong>
                <small>{{ session.preview }}</small>
              </button>
              <span>{{ session.messageCount }}</span>
              <button class="session-delete" @click="deleteSession(session)" title="删除对话">
                <Trash2 class="h-3.5 w-3.5" />
              </button>
            </div>
          </div>
        </section>
      </aside>

      <main class="ai-main">
        <section class="feature-grid">
          <button
            v-for="item in featureCards"
            :key="item.title"
            @click="sendPrompt(item.prompt)"
          >
            <component :is="item.icon" class="h-5 w-5" />
            <span>{{ item.title }}</span>
            <small>{{ item.text }}</small>
          </button>
        </section>

        <div v-if="loadingMessages" class="chat-loading">
          <RefreshCw class="h-5 w-5 animate-spin" />
          正在载入对话
        </div>
        <AIChatPanel
          v-else
          ref="aiChatRef"
          :chat-id="activeChatId"
          :messages="activeMessages"
          @sent="handleSent"
          @refresh-sessions="loadSessions"
        />
      </main>
    </div>
  </AppLayout>
</template>

<style scoped>
.ai-workbench {
  display: grid;
  grid-template-columns: minmax(260px, 320px) minmax(0, 1fr);
  gap: 1.25rem;
  align-items: start;
}

.ai-sidebar {
  display: grid;
  gap: 1rem;
  position: sticky;
  top: 1rem;
}

.sidebar-card {
  border: 1px solid rgba(121, 104, 78, 0.22);
  border-radius: 8px;
  background: rgba(255, 253, 246, 0.94);
  box-shadow: 0 12px 28px rgba(62, 58, 53, 0.06);
  padding: 0.9rem;
}

.new-chat-button,
.provider-button {
  width: 100%;
  min-height: 2.6rem;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  border-radius: 8px;
  font-weight: 700;
}

.new-chat-button {
  background: var(--ink-pine);
  color: #fffdf6;
}

.provider-block {
  position: relative;
  margin-top: 0.75rem;
}

.provider-button {
  border: 1px solid rgba(121, 104, 78, 0.22);
  background: rgba(247, 243, 232, 0.6);
  color: var(--ink-stone);
}

.provider-dot {
  width: 0.55rem;
  height: 0.55rem;
  border-radius: 999px;
}

.provider-dot.live {
  background: var(--ink-pine);
}

.provider-dot.mock {
  background: var(--ink-muted);
}

.rotate {
  transform: rotate(180deg);
}

.provider-menu {
  position: absolute;
  z-index: 20;
  inset-inline: 0;
  top: calc(100% + 0.35rem);
  border: 1px solid rgba(121, 104, 78, 0.22);
  border-radius: 8px;
  background: #fffdf6;
  box-shadow: 0 16px 34px rgba(62, 58, 53, 0.14);
  overflow: hidden;
}

.provider-menu button {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem;
  color: var(--ink-stone);
}

.provider-menu button:hover {
  background: rgba(59, 107, 87, 0.08);
}

.section-heading {
  margin-bottom: 0.7rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--ink-stone);
  font-weight: 700;
}

.section-heading button {
  color: var(--ink-muted);
}

.question-list,
.session-list {
  display: grid;
  gap: 0.55rem;
}

.question-list button {
  text-align: left;
  border: 1px solid rgba(121, 104, 78, 0.16);
  border-radius: 8px;
  background: rgba(247, 243, 232, 0.45);
  padding: 0.75rem;
  transition: border-color 0.16s ease, background 0.16s ease;
}

.question-list button:hover,
.session-list button:hover {
  border-color: rgba(59, 107, 87, 0.36);
  background: rgba(59, 107, 87, 0.07);
}

.question-list span {
  display: inline-block;
  margin-bottom: 0.35rem;
  color: var(--ink-pine-dark);
  font-size: 0.75rem;
  font-weight: 700;
}

.question-list strong,
.session-list strong {
  display: block;
  color: var(--ink-stone);
  font-size: 0.9rem;
  line-height: 1.45;
}

.question-list small,
.session-list small {
  display: block;
  margin-top: 0.35rem;
  color: var(--ink-muted);
  font-size: 0.76rem;
  line-height: 1.5;
}

.session-card {
  max-height: 38vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.session-list {
  overflow-y: auto;
  padding-right: 0.1rem;
}

.session-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 0.55rem;
  align-items: center;
  text-align: left;
  border: 1px solid rgba(121, 104, 78, 0.16);
  border-radius: 8px;
  background: rgba(247, 243, 232, 0.4);
  padding: 0.65rem;
}

.session-item.active {
  border-color: var(--ink-pine);
  background: rgba(59, 107, 87, 0.09);
}

.session-main {
  min-width: 0;
  text-align: left;
}

.session-list span {
  color: var(--ink-light);
  font-size: 0.75rem;
}

.session-delete {
  color: var(--ink-muted);
}

.loading-line,
.empty-line {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  color: var(--ink-muted);
  font-size: 0.9rem;
}

.ai-main {
  min-width: 0;
  display: grid;
  gap: 1rem;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.75rem;
}

.feature-grid button {
  min-height: 6rem;
  text-align: left;
  border: 1px solid rgba(121, 104, 78, 0.2);
  border-radius: 8px;
  background: rgba(255, 253, 246, 0.94);
  padding: 0.85rem;
  color: var(--ink-stone);
  transition: border-color 0.16s ease, transform 0.16s ease;
}

.feature-grid button:hover {
  border-color: rgba(59, 107, 87, 0.38);
  transform: translateY(-1px);
}

.feature-grid svg {
  color: var(--ink-pine-dark);
}

.feature-grid span {
  display: block;
  margin-top: 0.55rem;
  font-weight: 700;
}

.feature-grid small {
  display: block;
  margin-top: 0.35rem;
  color: var(--ink-muted);
  line-height: 1.5;
}

.chat-loading {
  min-height: 72vh;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  color: var(--ink-muted);
}

@media (max-width: 1080px) {
  .ai-workbench {
    grid-template-columns: 1fr;
  }

  .ai-sidebar {
    position: static;
  }

  .feature-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .feature-grid {
    grid-template-columns: 1fr;
  }
}
</style>
