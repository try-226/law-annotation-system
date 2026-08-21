<script setup lang="ts">
defineProps<{
  open: boolean
  title: string
  busy?: boolean
  width?: string
}>()

const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="open" class="modal-mask" @mousedown.self="!busy && emit('close')">
        <section
          class="modal-card"
          :style="{ width: width || '520px' }"
          role="dialog"
          aria-modal="true"
          :aria-label="title"
        >
          <header class="modal-header">
            <h2>{{ title }}</h2>
            <button type="button" aria-label="关闭" :disabled="busy" @click="emit('close')">×</button>
          </header>
          <div class="modal-body"><slot /></div>
          <footer v-if="$slots.footer" class="modal-footer"><slot name="footer" /></footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-mask {
  position: fixed;
  z-index: 900;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgb(15 23 42 / 46%);
}

.modal-card {
  max-width: 100%;
  max-height: calc(100vh - 48px);
  overflow: auto;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 18px 50px rgb(15 23 42 / 24%);
}

.modal-header, .modal-footer { display: flex; align-items: center; padding: 18px 22px; }
.modal-header { justify-content: space-between; border-bottom: 1px solid #e7ebf0; }
.modal-header h2 { margin: 0; font-size: 19px; }
.modal-header button { border: 0; background: transparent; color: #667085; font-size: 24px; cursor: pointer; }
.modal-header button:disabled { cursor: not-allowed; opacity: 0.5; }
.modal-body { padding: 22px; }
.modal-footer { justify-content: flex-end; gap: 10px; border-top: 1px solid #e7ebf0; }
.modal-enter-active, .modal-leave-active { transition: opacity 0.16s ease; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
</style>
