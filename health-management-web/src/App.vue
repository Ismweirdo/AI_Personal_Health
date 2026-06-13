<script setup lang="ts">
import AnimatedBg from './components/common/AnimatedBg.vue';
import { RouterView } from 'vue-router';
</script>

<template>
  <AnimatedBg />
  <main class="app-container">
    <RouterView />
  </main>
</template>

<style>
@import './style.css';

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family:
    "Noto Serif SC", "Songti SC", "STSong", "Noto Sans SC",
    -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  color: var(--ink-stone);
  background-color: var(--ink-paper);
  min-height: 100vh;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

.app-container {
  position: relative;
  min-height: 100vh;
  z-index: 1;
}
</style>
