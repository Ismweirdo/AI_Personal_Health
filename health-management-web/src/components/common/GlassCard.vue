<script setup lang="ts">
import { ref, onMounted } from 'vue';

interface Props {
  blur?: number;
  transparency?: number;
  borderRadius?: string;
  className?: string;
  hoverEffect?: boolean;
  animationDelay?: string;
  animationType?: 'fade-in' | 'fade-in-up' | 'fade-in-down' | 'scale-in';
}

const props = withDefaults(defineProps<Props>(), {
  blur: 0,
  transparency: 0.94,
  borderRadius: '8px',
  className: '',
  hoverEffect: true,
  animationDelay: '0ms',
  animationType: 'fade-in-up'
});

const isVisible = ref(false);

onMounted(() => {
  setTimeout(() => {
    isVisible.value = true;
  }, parseInt(props.animationDelay) || 0);
});
</script>

<template>
  <div
    :class="[
      'bili-card-component',
      hoverEffect ? 'bili-card-hover' : '',
      isVisible ? `animate-${animationType}` : 'opacity-0',
      className
    ]"
    :style="{
      '--animation-delay': animationDelay
    }"
  >
    <!-- 内容 -->
    <div class="card-content relative z-10">
      <slot></slot>
    </div>
  </div>
</template>

<style scoped>
.bili-card-component {
  background: rgba(255, 253, 246, 0.94);
  border: 1px solid rgba(121, 104, 78, 0.22);
  border-radius: 8px;
  box-shadow: 0 14px 36px rgba(62, 58, 53, 0.08);
  padding: 1.5rem;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
  overflow: hidden;
  position: relative;
}

.bili-card-hover:hover {
  box-shadow: 0 18px 44px rgba(62, 58, 53, 0.12);
  border-color: rgba(59, 107, 87, 0.34);
}

/* 进入动画 */
@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes fadeInDown {
  from { opacity: 0; transform: translateY(-12px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes scaleIn {
  from { opacity: 0; transform: scale(0.95); }
  to { opacity: 1; transform: scale(1); }
}

.animate-fade-in {
  animation: fadeIn 0.5s ease-out forwards;
  animation-delay: var(--animation-delay);
}

.animate-fade-in-up {
  animation: fadeInUp 0.5s ease-out forwards;
  animation-delay: var(--animation-delay);
}

.animate-fade-in-down {
  animation: fadeInDown 0.5s ease-out forwards;
  animation-delay: var(--animation-delay);
}

.animate-scale-in {
  animation: scaleIn 0.4s ease-out forwards;
  animation-delay: var(--animation-delay);
}
</style>
