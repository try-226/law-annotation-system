<script setup lang="ts">
import { ARTICLE_FIELD_LABELS, ITEM_TYPE_LABELS, type ArticleDraftForm } from '../../types/annotation'
import type { TaskArticleSnapshot, TaskDetail } from '../../types/task'
import { shouldShowRequiredMarker } from './annotationDraftState'

const props = defineProps<{
  task: TaskDetail
  article: TaskArticleSnapshot
  structurePath: string
  modelValue: ArticleDraftForm
  errors: Record<string, string>
  editable: boolean
  completed: boolean
  revisionStatus: string | null
}>()

const emit = defineEmits<{ 'update:modelValue': [value: ArticleDraftForm] }>()

function update(field: keyof ArticleDraftForm, value: string): void {
  emit('update:modelValue', { ...props.modelValue, [field]: value })
}

function required(field: keyof ArticleDraftForm): boolean {
  return shouldShowRequiredMarker(
    props.task,
    { kind: 'article', articleId: props.article.articleId },
    props.task.fieldConfigSnapshot.article,
    field,
    props.editable,
  )
}
</script>

<template>
  <div class="annotation-panel-content">
    <header class="annotation-panel-heading">
      <div><h2>{{ article.number }}</h2><p>章节路径：{{ structurePath || '未归入结构节点' }}</p></div>
      <span v-if="revisionStatus" class="annotation-state-pill revision">{{ revisionStatus }}</span>
      <span v-else class="annotation-state-pill" :class="{ complete: completed }">{{ completed ? '已完成' : '未完成' }}</span>
    </header>
    <section class="article-body"><p>{{ article.body }}</p></section>

    <section class="annotation-form-section">
      <h3>法条标注</h3>
      <div class="annotation-form-grid">
        <div class="form-field">
          <label for="annotation-itemType">{{ ARTICLE_FIELD_LABELS.itemType }} <span v-if="required('itemType')" class="required">*</span></label>
          <select id="annotation-itemType" class="select" :value="modelValue.itemType" :disabled="!editable" @change="update('itemType', ($event.target as HTMLSelectElement).value)">
            <option value="">请选择</option><option v-for="(label, value) in ITEM_TYPE_LABELS" :key="value" :value="value">{{ label }}</option>
          </select>
          <p v-if="errors.itemType" class="field-error">{{ errors.itemType }}</p>
        </div>
        <div class="form-field">
          <label for="annotation-keywords">{{ ARTICLE_FIELD_LABELS.keywords }} <span v-if="required('keywords')" class="required">*</span></label>
          <input id="annotation-keywords" class="input" :value="modelValue.keywords" :disabled="!editable" placeholder="多个关键词用逗号分隔" @input="update('keywords', ($event.target as HTMLInputElement).value)" />
          <p v-if="errors.keywords" class="field-error">{{ errors.keywords }}</p>
        </div>
        <div class="form-field">
          <label for="annotation-subjects">{{ ARTICLE_FIELD_LABELS.subjects }} <span v-if="required('subjects')" class="required">*</span></label>
          <input id="annotation-subjects" class="input" :value="modelValue.subjects" :disabled="!editable" maxlength="200" @input="update('subjects', ($event.target as HTMLInputElement).value)" />
          <p v-if="errors.subjects" class="field-error">{{ errors.subjects }}</p>
        </div>
        <div class="form-field">
          <label for="annotation-legalLiability">{{ ARTICLE_FIELD_LABELS.legalLiability }} <span v-if="required('legalLiability')" class="required">*</span></label>
          <textarea id="annotation-legalLiability" class="annotation-textarea" rows="3" :value="modelValue.legalLiability" :disabled="!editable" maxlength="1000" @input="update('legalLiability', ($event.target as HTMLTextAreaElement).value)" />
          <p v-if="errors.legalLiability" class="field-error">{{ errors.legalLiability }}</p>
        </div>
        <div class="form-field annotation-form-wide">
          <label for="annotation-annotationNote">{{ ARTICLE_FIELD_LABELS.annotationNote }} <span v-if="required('annotationNote')" class="required">*</span></label>
          <textarea id="annotation-annotationNote" class="annotation-textarea" rows="4" :value="modelValue.annotationNote" :disabled="!editable" maxlength="1000" @input="update('annotationNote', ($event.target as HTMLTextAreaElement).value)" />
          <p v-if="errors.annotationNote" class="field-error">{{ errors.annotationNote }}</p>
        </div>
      </div>
    </section>
  </div>
</template>
