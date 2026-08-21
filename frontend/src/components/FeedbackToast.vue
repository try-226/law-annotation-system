<script setup lang="ts">
import { dismissNotice, noticeState } from '../state/notifications'
</script>

<template>
  <Transition name="toast">
    <div v-if="noticeState.visible" class="toast" :class="`toast--${noticeState.type}`" role="status">
      <span>{{ noticeState.message }}</span>
      <button type="button" aria-label="关闭提示" @click="dismissNotice">×</button>
    </div>
  </Transition>
</template>

<style scoped>
.toast {
  position: fixed;
  z-index: 1000;
  top: 24px;
  left: 50%;
  display: flex;
  min-width: 320px;
  max-width: min(560px, calc(100vw - 32px));
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 13px 16px;
  border: 1px solid #d7dde7;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 8px 24px rgb(15 23 42 / 14%);
  color: #263244;
  transform: translateX(-50%);
}

.toast--success { border-left: 4px solid #1c8b59; }
.toast--error { border-left: 4px solid #d64545; }
.toast--info { border-left: 4px solid #2868c7; }
.toast button { border: 0; background: transparent; color: #718096; font-size: 20px; cursor: pointer; }
.toast-enter-active, .toast-leave-active { transition: opacity 0.18s ease, transform 0.18s ease; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translate(-50%, -8px); }
</style>
