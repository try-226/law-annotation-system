<script setup lang="ts">
import { computed, ref } from 'vue'

import AppModal from '../../components/AppModal.vue'
import { normalizeIssueReason, validateIssueReason } from './reviewState'

const props = defineProps<{
  open: boolean
  targetLabel: string
  reason: string
  busy: boolean
  serverError: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  close: []
  confirm: [reason: string]
  'update:reason': [reason: string]
}>()

const localError = ref('')
const characterCount = computed(() => Array.from(props.reason.trim()).length)

function submit(): void {
  const error = validateIssueReason(props.reason)
  localError.value = error ?? ''
  if (error || props.busy || props.disabled) return
  emit('confirm', normalizeIssueReason(props.reason))
}

function updateReason(value: string): void {
  localError.value = ''
  emit('update:reason', value)
}
</script>

<template>
  <AppModal :open="open" title="标记为待修改" :busy="busy" width="620px" @close="emit('close')">
    <form class="review-issue-form" @submit.prevent="submit">
      <p class="review-modal-target"><strong>对象：</strong>{{ targetLabel }}</p>
      <div class="form-field">
        <label for="review-issue-reason">问题原因 <span class="required">*</span></label>
        <textarea
          id="review-issue-reason"
          :value="reason"
          class="review-textarea"
          rows="7"
          :disabled="busy"
          placeholder="请说明具体字段及修改要求"
          @input="updateReason(($event.target as HTMLTextAreaElement).value)"
        />
        <div class="review-reason-meta">
          <p v-if="localError || serverError" class="field-error">{{ localError || serverError }}</p>
          <span :class="{ 'review-count-error': characterCount > 500 }">{{ characterCount }} / 500</span>
        </div>
      </div>
      <p v-if="disabled" class="review-modal-warning">服务器状态已变化，当前内容未提交。你可以保留或复制输入后关闭弹窗。</p>
    </form>
    <template #footer>
      <button class="button" type="button" :disabled="busy" @click="emit('close')">取消</button>
      <button class="button button--danger" type="button" :disabled="busy || disabled" @click="submit">
        <span v-if="busy" class="spinner" />{{ busy ? '保存中…' : '确认标记' }}
      </button>
    </template>
  </AppModal>
</template>
