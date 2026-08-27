<script setup lang="ts">
import type { OverallDraftForm } from '../../types/annotation'
import { LAW_CATEGORY_OPTIONS } from '../../types/annotation'
import type { ValidityStatus } from '../../types/law'
import type { TaskDetail } from '../../types/task'
import { isFieldRequired } from './annotationDraftState'

const props = defineProps<{
  task: TaskDetail
  modelValue: OverallDraftForm
  errors: Record<string, string>
  editable: boolean
  revisionStatus: string | null
}>()

const emit = defineEmits<{ 'update:modelValue': [value: OverallDraftForm] }>()

const validityLabels: Record<ValidityStatus, string> = {
  ACTIVE: '现行有效', NOT_EFFECTIVE: '尚未生效', INVALID: '失效', REPEALED: '已废止',
}

function update(field: keyof OverallDraftForm, value: string): void {
  emit('update:modelValue', { ...props.modelValue, [field]: value })
}

function required(field: keyof OverallDraftForm): boolean {
  return isFieldRequired(props.task.fieldConfigSnapshot.overall, field)
}
</script>

<template>
  <div class="annotation-panel-content">
    <header class="annotation-panel-heading">
      <div><h2>整体信息</h2><p>法律基础信息只读，标注字段按任务创建时配置填写。</p></div>
      <span v-if="revisionStatus" class="annotation-state-pill revision">{{ revisionStatus }}</span>
      <span v-else class="annotation-state-pill">整体标注</span>
    </header>

    <dl class="annotation-law-meta">
      <div><dt>法律名称</dt><dd>{{ task.lawBaseInfoSnapshot.name }}</dd></div>
      <div><dt>发布机关</dt><dd>{{ task.lawBaseInfoSnapshot.issuingAuthority }}</dd></div>
      <div><dt>发布日期</dt><dd>{{ task.lawBaseInfoSnapshot.publicationDate }}</dd></div>
      <div><dt>效力状态</dt><dd>{{ validityLabels[task.lawBaseInfoSnapshot.validityStatus] }}</dd></div>
    </dl>

    <section class="annotation-form-section">
      <h3>整体标注</h3>
      <div class="annotation-form-grid">
        <div class="form-field">
          <label for="annotation-lawCategory">法律类别 <span v-if="required('lawCategory')" class="required">*</span></label>
          <select id="annotation-lawCategory" class="select" :value="modelValue.lawCategory" :disabled="!editable" @change="update('lawCategory', ($event.target as HTMLSelectElement).value)">
            <option value="">请选择</option><option v-for="option in LAW_CATEGORY_OPTIONS" :key="option" :value="option">{{ option }}</option>
          </select>
          <p v-if="errors.lawCategory" class="field-error">{{ errors.lawCategory }}</p>
        </div>
        <div class="form-field">
          <label for="annotation-overallKeywords">整体关键词 <span v-if="required('overallKeywords')" class="required">*</span></label>
          <input id="annotation-overallKeywords" class="input" :value="modelValue.overallKeywords" :disabled="!editable" placeholder="多个关键词用逗号分隔" @input="update('overallKeywords', ($event.target as HTMLInputElement).value)" />
          <p v-if="errors.overallKeywords" class="field-error">{{ errors.overallKeywords }}</p>
        </div>
        <div class="form-field annotation-form-wide">
          <label for="annotation-summary">摘要 <span v-if="required('summary')" class="required">*</span></label>
          <textarea id="annotation-summary" class="annotation-textarea" rows="6" :value="modelValue.summary" :disabled="!editable" maxlength="2000" @input="update('summary', ($event.target as HTMLTextAreaElement).value)" />
          <p v-if="errors.summary" class="field-error">{{ errors.summary }}</p>
        </div>
        <div class="form-field annotation-form-wide">
          <label for="annotation-overallNote">备注 <span v-if="required('overallNote')" class="required">*</span></label>
          <textarea id="annotation-overallNote" class="annotation-textarea" rows="4" :value="modelValue.overallNote" :disabled="!editable" maxlength="1000" @input="update('overallNote', ($event.target as HTMLTextAreaElement).value)" />
          <p v-if="errors.overallNote" class="field-error">{{ errors.overallNote }}</p>
        </div>
      </div>
    </section>
  </div>
</template>
