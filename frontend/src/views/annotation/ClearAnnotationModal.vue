<script setup lang="ts">
import AppModal from '../../components/AppModal.vue'

defineProps<{
  open: boolean
  title: string
  description: string
  confirmLabel: string
  busy?: boolean
}>()
const emit = defineEmits<{ confirm: []; close: [] }>()
</script>

<template>
  <AppModal :open="open" :title="title" :busy="busy" @close="emit('close')">
    <p class="annotation-modal-copy">{{ description }}</p>
    <template #footer>
      <button class="button" type="button" :disabled="busy" @click="emit('close')">取消</button>
      <button class="button button--danger" type="button" :disabled="busy" @click="emit('confirm')"><span v-if="busy" class="spinner" />{{ confirmLabel }}</button>
    </template>
  </AppModal>
</template>
