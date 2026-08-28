<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { exportLaw } from '../../api/export'
import type { ExportFormat, ExportScope, ExportType } from '../../types/export'
import type { AnnotationVersionHistory } from '../../types/history'
import type { LawDetail } from '../../types/law'
import { notify } from '../../state/notifications'
import AppModal from '../../components/AppModal.vue'
import {
  buildLawExportRequest,
  ExportSelectionError,
  formalAvailability,
  triggerBlobDownload,
} from './exportDownload'
import { exportFailureMessage } from './exportFailure'

const props = defineProps<{
  open: boolean
  law: LawDetail
  selectedArticleIds: string[]
  annotation: AnnotationVersionHistory | null
  formalLoadError: string
}>()
const emit = defineEmits<{ close: [] }>()

const scope = ref<ExportScope>('WHOLE')
const type = ref<ExportType>('PLAIN')
const format = ref<ExportFormat>('CSV')
const busy = ref(false)
const error = ref('')
const availability = computed(() => formalAvailability(
  props.law,
  props.annotation,
  Boolean(props.formalLoadError),
))

watch(() => props.open, (open) => {
  if (!open) return
  error.value = ''
  if (type.value === 'FORMAL' && !availability.value.available) type.value = 'PLAIN'
})

async function submit(): Promise<void> {
  if (busy.value) return
  if (type.value === 'FORMAL' && !availability.value.available) {
    error.value = availability.value.message
    return
  }
  let payload
  try {
    payload = buildLawExportRequest(
      scope.value,
      props.selectedArticleIds,
      type.value,
      format.value,
    )
  } catch (caught: unknown) {
    error.value = caught instanceof ExportSelectionError ? caught.message : '导出选择不合法'
    return
  }

  busy.value = true
  error.value = ''
  try {
    const download = await exportLaw(props.law.id, payload)
    triggerBlobDownload(download.blob, download.filename)
    notify('导出文件已开始下载', 'success')
    emit('close')
  } catch (caught: unknown) {
    error.value = exportFailureMessage(caught)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <AppModal :open="open" title="导出法律" :busy="busy" width="620px" @close="emit('close')">
    <div class="export-modal-content">
      <p class="export-summary"><strong>{{ law.name }}</strong><span>已选择 {{ selectedArticleIds.length }} / {{ law.articles.length }} 条法条</span></p>

      <fieldset class="export-option-group">
        <legend>导出范围</legend>
        <label><input v-model="scope" type="radio" value="WHOLE" :disabled="busy" /><span><strong>整部法律</strong><small>导出当前法律全部法条</small></span></label>
        <label><input v-model="scope" type="radio" value="SELECTED" :disabled="busy" /><span><strong>已选法条</strong><small>仅导出当前勾选的 {{ selectedArticleIds.length }} 条</small></span></label>
      </fieldset>

      <fieldset class="export-option-group">
        <legend>导出内容</legend>
        <label><input v-model="type" type="radio" value="PLAIN" :disabled="busy" /><span><strong>纯法律正文</strong><small>不要求存在正式标注</small></span></label>
        <label :class="{ 'is-disabled': !availability.available }"><input v-model="type" type="radio" value="FORMAL" :disabled="busy || !availability.available" /><span><strong>正式标注结果</strong><small>{{ availability.message }}</small></span></label>
      </fieldset>

      <fieldset class="export-option-group export-format-options">
        <legend>文件格式</legend>
        <label><input v-model="format" type="radio" value="CSV" :disabled="busy" /><span><strong>CSV</strong></span></label>
        <label><input v-model="format" type="radio" value="JSON" :disabled="busy" /><span><strong>JSON</strong></span></label>
      </fieldset>

      <p v-if="error" class="export-error" role="alert">{{ error }}</p>
    </div>
    <template #footer>
      <button class="button" type="button" :disabled="busy" @click="emit('close')">取消</button>
      <button class="button button--primary" type="button" :disabled="busy" @click="submit">
        <span v-if="busy" class="spinner" aria-hidden="true"></span>{{ busy ? '导出中…' : '开始导出' }}
      </button>
    </template>
  </AppModal>
</template>

<style src="./export.css"></style>
