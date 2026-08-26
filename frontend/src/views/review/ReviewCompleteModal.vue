<script setup lang="ts">
import AppModal from '../../components/AppModal.vue'
import type { ReviewDetail } from '../../types/review'

const props = defineProps<{
  open: boolean
  review: ReviewDetail
  busy: boolean
  error: string
  disabled: boolean
}>()

const emit = defineEmits<{
  close: []
  confirm: []
}>()
</script>

<template>
  <AppModal :open="open" title="完成本轮审核" :busy="busy" width="560px" @close="emit('close')">
    <div class="review-complete-copy">
      <p>完成后本轮审核将锁定，不能继续修改审核结论。</p>
      <dl>
        <div><dt>已审核</dt><dd>{{ review.progress.reviewed }} / {{ review.progress.total }}</dd></div>
        <div><dt>未审核</dt><dd>{{ review.progress.unreviewed }}</dd></div>
        <div><dt>存在问题</dt><dd>{{ review.progress.needsChange }}</dd></div>
      </dl>
      <p v-if="review.progress.unreviewed > 0" class="review-modal-warning">
        尚有 {{ review.progress.unreviewed }} 个审核项未处理，当前不能完成本轮。
      </p>
      <p v-else class="review-outcome-preview">
        服务器将根据当前问题数生成
        <strong>{{ review.progress.needsChange === 0 ? '审核通过' : '部分驳回' }}</strong>
        结果，最终以完成接口返回值为准。
      </p>
      <p v-if="error" class="field-error">{{ error }}</p>
    </div>
    <template #footer>
      <button class="button" type="button" :disabled="busy" @click="emit('close')">取消</button>
      <button
        class="button button--primary"
        type="button"
        :disabled="busy || disabled"
        @click="emit('confirm')"
      >
        <span v-if="busy" class="spinner" />{{ busy ? '完成中…' : '确认完成' }}
      </button>
    </template>
  </AppModal>
</template>
