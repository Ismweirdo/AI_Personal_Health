<script setup lang="ts">
import { useRouter } from 'vue-router';
import { Home } from 'lucide-vue-next';

const router = useRouter();
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-[var(--ink-paper)]">
    <div class="text-center">
      <h1 class="mb-4 text-8xl font-bold text-[var(--ink-pine-dark)]">404</h1>
      <p class="mb-8 text-xl text-[var(--ink-muted)]">页面未找到</p>
      <button
        @click="router.push('/')"
        class="inline-flex items-center gap-2 rounded-lg border border-[var(--ink-line)] bg-[rgba(255,253,246,0.88)] px-6 py-3 font-medium text-[var(--ink-pine-dark)] transition-colors hover:border-[var(--ink-pine)]"
      >
        <Home class="w-5 h-5" />
        返回首页
      </button>
    </div>
  </div>
</template>
