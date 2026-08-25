<script setup lang="ts">
import AppModal from '../../components/AppModal.vue'

defineProps<{ open: boolean; mode: 'switch' | 'leave'; busy?: boolean }>()
const emit = defineEmits<{ save: []; discard: []; close: [] }>()
</script>

<template>
  <AppModal :open="open" title="存在未保存修改" :busy="busy" @close="emit('close')">
    <p class="annotation-modal-copy">当前标注有尚未保存的修改。请选择如何处理后再{{ mode === 'switch' ? '切换位置' : '离开工作台' }}。</p>
    <template #footer>
      <button class="button" type="button" :disabled="busy" @click="emit('close')">取消</button>
      <button class="button" type="button" :disabled="busy" @click="emit('discard')">{{ mode === 'switch' ? '放弃并切换' : '直接离开' }}</button>
      <button class="button button--primary" type="button" :disabled="busy" @click="emit('save')"><span v-if="busy" class="spinner" />{{ mode === 'switch' ? '保存并切换' : '保存并离开' }}</button>
    </template>
  </AppModal>
</template>
