<script setup lang="ts">
import { ref, watch } from 'vue'

import AppModal from '../../components/AppModal.vue'
import type { TaskListItem, TaskState } from '../../types/task'

const props = defineProps<{
  open: boolean
  task: Pick<TaskListItem, 'taskName' | 'taskState'> | null
  busy?: boolean
  serverError?: string
}>()

const emit = defineEmits<{
  close: []
  confirm: [reason: string]
}>()

const reason = ref('')
const reasonError = ref('')

watch(
  () => props.open,
  (open) => {
    if (open) {
      reason.value = ''
      reasonError.value = ''
    }
  },
)

function validateReason(value: string): string | null {
  const trimmed = value.trim()
  const length = [...trimmed].length
  if (!trimmed) return '请输入取消原因'
  if (length > 500) return '取消原因不能超过500个字符'
  if ([...trimmed].some((character) => /[\u0000-\u001f\u007f]/.test(character))) {
    return '取消原因不能包含控制字符'
  }
  return null
}

function submit(): void {
  reasonError.value = validateReason(reason.value) ?? ''
  if (reasonError.value || props.busy) return
  emit('confirm', reason.value.trim())
}

function stateIs(value: TaskState): boolean {
  return props.task?.taskState === value
}
</script>

<template>
  <AppModal :open="open" title="确认取消任务" :busy="busy" @close="emit('close')">
    <form id="cancel-task-form" class="form-grid" @submit.prevent="submit">
      <p class="modal-copy">确定取消任务 <strong>{{ task?.taskName }}</strong> 吗？取消后仍可只读查看任务详情。</p>
      <p v-if="stateIs('ANNOTATING')" class="danger-note">
        任务取消后，当前任务草稿不能继续作为新任务使用。
      </p>
      <div class="form-field">
        <label for="cancel-reason">取消原因 <span class="required">*</span></label>
        <textarea
          id="cancel-reason"
          v-model="reason"
          class="task-textarea"
          rows="5"
          maxlength="500"
          placeholder="请输入1至500个字符"
          :disabled="busy"
        />
        <div class="field-row"><p v-if="reasonError" class="field-error">{{ reasonError }}</p><span>{{ [...reason.trim()].length }} / 500</span></div>
      </div>
      <p v-if="serverError" class="field-error">{{ serverError }}</p>
    </form>
    <template #footer>
      <button class="button" type="button" :disabled="busy" @click="emit('close')">返回</button>
      <button class="button button--danger" type="submit" form="cancel-task-form" :disabled="busy">
        <span v-if="busy" class="spinner" />{{ busy ? '取消中…' : '确认取消任务' }}
      </button>
    </template>
  </AppModal>
</template>

<style scoped>
.modal-copy { margin: 0; color: #465264; line-height: 1.7; }
.danger-note { margin: 0; border-radius: 6px; background: #fff1f1; color: #a43b3b; padding: 12px; font-size: 13px; line-height: 1.6; }
.task-textarea { width: 100%; resize: vertical; border: 1px solid #cfd6df; border-radius: 6px; outline: none; padding: 10px 12px; line-height: 1.6; }
.task-textarea:focus { border-color: #3b78d2; box-shadow: 0 0 0 3px rgb(59 120 210 / 12%); }
.task-textarea:disabled { background: #f4f6f8; }
.field-row { display: flex; min-height: 18px; justify-content: space-between; color: #8a94a4; font-size: 12px; }
.field-row .field-error { font-size: 13px; }
</style>
