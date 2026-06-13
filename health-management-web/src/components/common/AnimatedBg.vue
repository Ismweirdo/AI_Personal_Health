<template>
  <div class="animated-bg">
    <div class="paper-fibers"></div>
    <div class="ink-wash ink-wash-left"></div>
    <div class="ink-wash ink-wash-right"></div>
  </div>
</template>

<style scoped>
.animated-bg {
  position: fixed;
  inset: 0;
  z-index: -1;
  overflow: hidden;
  background:
    linear-gradient(180deg, rgba(255, 253, 246, 0.96), rgba(247, 243, 232, 0.98)),
    #f7f3e8;
}

.paper-fibers {
  position: absolute;
  inset: 0;
  opacity: 0.34;
  background-image:
    repeating-linear-gradient(0deg, rgba(62, 58, 53, 0.035) 0 1px, transparent 1px 18px),
    repeating-linear-gradient(90deg, rgba(62, 58, 53, 0.025) 0 1px, transparent 1px 24px);
}

.ink-wash {
  position: absolute;
  width: min(42vw, 520px);
  aspect-ratio: 1;
  border-radius: 50%;
  filter: blur(48px);
  opacity: 0.12;
}

.ink-wash-left {
  left: -18vw;
  top: 18vh;
  background: #3e3a35;
}

.ink-wash-right {
  right: -20vw;
  bottom: 8vh;
  background: #3b6b57;
}

@media (max-width: 768px) {
  .ink-wash {
    width: 70vw;
    opacity: 0.08;
  }
}
</style>
