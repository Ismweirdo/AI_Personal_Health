<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  ArrowLeft,
  Bell,
  BookOpen,
  Bot,
  FileText,
  Flag,
  Home,
  LogOut,
  Menu,
  Smartphone,
  Users,
  X
} from 'lucide-vue-next';
import { clearAuth, logout } from '../../api/auth';
import { getNotifications, type NotificationResponse } from '../../api/reminders';

const props = withDefaults(defineProps<{
  title: string;
  subtitle?: string;
  showBack?: boolean;
  backPath?: string;
}>(), {
  subtitle: '',
  showBack: false,
  backPath: '/',
});

const router = useRouter();
const route = useRoute();
const isMenuOpen = ref(false);
const isScrolled = ref(false);
const isLoaded = ref(false);
const unreadCount = ref(0);
const browserPermission = ref<'unsupported' | NotificationPermission>('unsupported');
let notificationPollTimer: number | null = null;

const navItems = [
  { path: '/', label: '首页', icon: Home },
  { path: '/goals', label: '目标', icon: Flag },
  { path: '/reminders', label: '提醒', icon: Bell },
  { path: '/family', label: '家庭', icon: Users },
  { path: '/reports', label: '报告', icon: FileText },
  { path: '/devices', label: '设备', icon: Smartphone },
  { path: '/api-guide', label: '接口指南', icon: BookOpen },
];

const handleLogout = async () => {
  try { await logout(); } catch { /* ignore */ }
  clearAuth();
  router.push('/login');
};

const handleScroll = () => {
  isScrolled.value = window.scrollY > 24;
};

const notifiedStorageKey = 'globalNotifiedReminderIds';

const unreadLabel = computed(() => unreadCount.value > 99 ? '99+' : String(unreadCount.value));

const getStoredNotifiedIds = () => {
  try {
    const raw = localStorage.getItem(notifiedStorageKey);
    return new Set<number>((raw ? JSON.parse(raw) : []).filter((id: unknown) => typeof id === 'number'));
  } catch {
    return new Set<number>();
  }
};

const saveStoredNotifiedIds = (ids: Set<number>) => {
  localStorage.setItem(notifiedStorageKey, JSON.stringify(Array.from(ids).slice(-300)));
};

const broadcastNotifications = (notifications: NotificationResponse[]) => {
  unreadCount.value = notifications.filter(item => item.status !== 'read').length;
  window.dispatchEvent(new CustomEvent('global-notifications-updated', {
    detail: {
      notifications,
      unreadCount: unreadCount.value
    }
  }));
};

const handleGlobalNotificationsUpdated = (event: Event) => {
  const customEvent = event as CustomEvent<{ notifications?: NotificationResponse[]; unreadCount?: number }>;
  if (typeof customEvent.detail?.unreadCount === 'number') {
    unreadCount.value = customEvent.detail.unreadCount;
    return;
  }
  if (Array.isArray(customEvent.detail?.notifications)) {
    unreadCount.value = customEvent.detail.notifications.filter(item => item.status !== 'read').length;
  }
};

const showBrowserNotifications = (notifications: NotificationResponse[]) => {
  if (browserPermission.value !== 'granted') {
    return;
  }

  const storedIds = getStoredNotifiedIds();
  const newUnread = notifications.filter(item => item.status !== 'read' && !storedIds.has(item.id));
  if (!newUnread.length) {
    return;
  }

  newUnread.forEach(item => {
    new Notification(item.title, {
      body: item.message || '你有一条新的健康提醒',
      tag: `global-reminder-${item.id}`
    });
    storedIds.add(item.id);
  });

  notifications
    .filter(item => item.status === 'read')
    .forEach(item => storedIds.add(item.id));

  saveStoredNotifiedIds(storedIds);
};

const syncNotifications = async () => {
  if (!localStorage.getItem('token')) {
    unreadCount.value = 0;
    return;
  }
  try {
    const res = await getNotifications();
    const notifications = res.data || [];
    broadcastNotifications(notifications);
    showBrowserNotifications(notifications);
  } catch {
    // 静默失败
  }
};

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

const startNotificationPolling = () => {
  stopNotificationPolling();
  notificationPollTimer = window.setInterval(() => {
    syncNotifications();
  }, 5000);
};

const stopNotificationPolling = () => {
  if (notificationPollTimer !== null) {
    window.clearInterval(notificationPollTimer);
    notificationPollTimer = null;
  }
};

onMounted(() => {
  setTimeout(() => {
    isLoaded.value = true;
  }, 80);
  handleScroll();
  window.addEventListener('scroll', handleScroll);
  window.addEventListener('global-notifications-updated', handleGlobalNotificationsUpdated as EventListener);
  requestBrowserPermission();
  syncNotifications();
  startNotificationPolling();
});

onBeforeUnmount(() => {
  window.removeEventListener('scroll', handleScroll);
  window.removeEventListener('global-notifications-updated', handleGlobalNotificationsUpdated as EventListener);
  stopNotificationPolling();
});
</script>

<template>
  <div class="app-layout">
    <header
      class="fixed top-0 left-0 right-0 z-50 transition-all duration-200"
      :class="isScrolled ? 'site-header site-header-scrolled' : 'site-header'"
    >
      <div class="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
        <button
          type="button"
          class="brand-mark"
          @click="router.push('/')"
          :class="isLoaded ? 'animate-fade-in-left' : 'opacity-0'"
          title="回到首页"
        >
          <span class="seal-mark">康</span>
          <span class="hidden text-lg font-semibold text-[var(--ink-stone)] sm:block">健康管家</span>
        </button>

        <nav class="hidden items-center gap-1 md:flex">
          <button
            v-for="item in navItems"
            :key="item.path"
            type="button"
            @click="router.push(item.path)"
            class="nav-button"
            :class="{ 'nav-button-active': route.path === item.path }"
          >
            <component :is="item.icon" class="h-4 w-4" />
            <span>{{ item.label }}</span>
            <span
              v-if="item.path === '/reminders' && unreadCount > 0"
              class="nav-badge"
            >
              {{ unreadLabel }}
            </span>
          </button>
        </nav>

        <div
          class="flex items-center gap-2"
          :class="isLoaded ? 'animate-fade-in-right' : 'opacity-0'"
        >
          <button
            type="button"
            @click="router.push('/reminders')"
            class="icon-button"
            title="提醒通知"
          >
            <Bell class="h-5 w-5" />
            <span v-if="unreadCount > 0" class="icon-badge">{{ unreadLabel }}</span>
          </button>

          <button
            type="button"
            @click="router.push('/ai-chat')"
            class="hidden items-center gap-2 rounded-lg border border-[var(--ink-line)] bg-[rgba(255,253,246,0.76)] px-3 py-2 text-sm font-medium text-[var(--ink-pine-dark)] transition hover:border-[var(--ink-pine)] sm:flex"
          >
            <Bot class="h-4 w-4" />
            <span>AI 助手</span>
          </button>

          <button
            type="button"
            @click="handleLogout"
            class="icon-button"
            title="退出登录"
          >
            <LogOut class="h-5 w-5" />
          </button>

          <button
            type="button"
            @click="isMenuOpen = !isMenuOpen"
            class="icon-button md:hidden"
            title="打开菜单"
          >
            <Menu v-if="!isMenuOpen" class="h-6 w-6" />
            <X v-else class="h-6 w-6" />
          </button>
        </div>
      </div>

      <transition
        enter-active-class="transition-all duration-200 ease-out"
        enter-from-class="opacity-0 -translate-y-2"
        enter-to-class="opacity-100 translate-y-0"
        leave-active-class="transition-all duration-150 ease-in"
        leave-from-class="opacity-100 translate-y-0"
        leave-to-class="opacity-0 -translate-y-2"
      >
        <div v-if="isMenuOpen" class="mobile-menu md:hidden">
          <nav class="space-y-1 px-4 py-4">
            <button
              v-for="item in navItems"
              :key="item.path"
              type="button"
              @click="router.push(item.path); isMenuOpen = false"
              class="mobile-nav-button"
              :class="{ 'mobile-nav-button-active': route.path === item.path }"
            >
              <component :is="item.icon" class="h-5 w-5" />
              <span>{{ item.label }}</span>
              <span
                v-if="item.path === '/reminders' && unreadCount > 0"
                class="ml-auto nav-badge"
              >
                {{ unreadLabel }}
              </span>
            </button>
            <button
              type="button"
              @click="router.push('/ai-chat'); isMenuOpen = false"
              class="mobile-nav-button"
            >
              <Bot class="h-5 w-5" />
              <span>AI 助手</span>
            </button>
          </nav>
        </div>
      </transition>
    </header>

    <main class="relative z-10 px-4 pb-14 pt-24 sm:px-6 lg:px-8">
      <div class="mx-auto max-w-7xl">
        <header
          class="mb-7 border-b border-[rgba(121,104,78,0.2)] pb-5"
          :class="isLoaded ? 'animate-fade-in-down' : 'opacity-0'"
        >
          <div class="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
            <slot name="header-left">
              <div class="flex items-start gap-3">
                <button
                  v-if="props.showBack"
                  type="button"
                  class="mt-1 rounded-lg border border-[var(--ink-line)] p-2 text-[var(--ink-muted)] transition hover:border-[var(--ink-pine)] hover:text-[var(--ink-pine-dark)]"
                  @click="router.push(props.backPath)"
                  title="返回"
                >
                  <ArrowLeft class="h-5 w-5" />
                </button>
                <div>
                  <h1 class="text-2xl font-semibold text-[var(--ink-stone)] md:text-3xl">
                    {{ title }}
                  </h1>
                  <p v-if="subtitle" class="mt-1 text-sm text-[var(--ink-muted)]">
                    {{ subtitle }}
                  </p>
                </div>
              </div>
            </slot>
            <div class="flex flex-wrap items-center gap-3">
              <slot name="header-right"></slot>
            </div>
          </div>
        </header>

        <div
          class="space-y-6"
          :class="isLoaded ? 'animate-fade-in' : 'opacity-0'"
        >
          <slot />
        </div>
      </div>
    </main>

    <footer class="relative z-10 border-t border-[rgba(121,104,78,0.18)] py-6">
      <div class="mx-auto max-w-7xl px-4 text-center text-sm text-[var(--ink-light)] sm:px-6 lg:px-8">
        健康管家 · 个人健康管理系统
      </div>
    </footer>
  </div>
</template>

<style scoped>
.app-layout {
  min-height: 100vh;
  background: transparent;
}

.site-header {
  border-bottom: 1px solid rgba(121, 104, 78, 0.18);
  background: rgba(247, 243, 232, 0.76);
  backdrop-filter: blur(16px);
}

.site-header-scrolled {
  background: rgba(255, 253, 246, 0.92);
  box-shadow: 0 10px 30px rgba(62, 58, 53, 0.08);
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  gap: 0.75rem;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.seal-mark {
  display: inline-flex;
  width: 2.45rem;
  height: 2.45rem;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(163, 74, 58, 0.42);
  border-radius: 6px;
  background: rgba(163, 74, 58, 0.08);
  color: var(--ink-cinnabar);
  font-family: "Noto Serif SC", "Songti SC", serif;
  font-size: 1.2rem;
  font-weight: 700;
}

.nav-button,
.mobile-nav-button {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  border-radius: 8px;
  color: var(--ink-muted);
  transition: background-color 0.16s ease, color 0.16s ease;
}

.nav-button {
  min-height: 2.4rem;
  padding: 0.5rem 0.8rem;
  font-size: 0.92rem;
}

.nav-button:hover,
.mobile-nav-button:hover,
.nav-button-active,
.mobile-nav-button-active {
  background: rgba(59, 107, 87, 0.1);
  color: var(--ink-pine-dark);
}

.nav-button-active {
  font-weight: 600;
}

.mobile-menu {
  border-top: 1px solid rgba(121, 104, 78, 0.18);
  background: rgba(255, 253, 246, 0.96);
  box-shadow: 0 18px 40px rgba(62, 58, 53, 0.12);
}

.mobile-nav-button {
  width: 100%;
  padding: 0.8rem 1rem;
}

.icon-button {
  position: relative;
  display: inline-flex;
  width: 2.45rem;
  height: 2.45rem;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--ink-line);
  border-radius: 8px;
  background: rgba(255, 253, 246, 0.76);
  color: var(--ink-muted);
  transition: border-color 0.16s ease, color 0.16s ease, background-color 0.16s ease;
}

.icon-button:hover {
  border-color: var(--ink-pine);
  background: rgba(59, 107, 87, 0.09);
  color: var(--ink-pine-dark);
}

.nav-badge,
.icon-badge {
  display: inline-flex;
  min-width: 1.25rem;
  height: 1.25rem;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: var(--ink-cinnabar);
  color: #fffdf6;
  font-size: 0.68rem;
  font-weight: 700;
}

.icon-badge {
  position: absolute;
  right: -0.35rem;
  top: -0.35rem;
}
</style>
