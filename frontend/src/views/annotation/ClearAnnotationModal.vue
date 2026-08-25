<script setup lang="ts">
import AppModal from '../../components/AppModal.vue'

defineProps<{ open: boolean; targetLabel: string; busy?: boolean }>()
const emit = defineEmits<{ confirm: []; close: [] }>()
</script>

<template>
  <AppModal :open="open" title="确认清空当前标注" :busy="busy" @close="emit('close')">
    <p class="annotation-modal-copy">确定清空“{{ targetLabel }}”已保存的标注吗？该区域会恢复为未完成状态。</p>
    <template #footer>
      <button class="button" type="button" :disabled="busy" @click="emit('close')">取消</button>
      <button class="button button--danger" type="button" :disabled="busy" @click="emit('confirm')"><span v-if="busy" class="spinner" />确认清空</button>
    </template>
  </AppModal>
</template>
