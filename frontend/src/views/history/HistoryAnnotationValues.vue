<script setup lang="ts">
import type { ArticleDraftValues, OverallDraftValues } from '../../types/annotation'
import { ITEM_TYPE_LABELS } from '../../types/annotation'

defineProps<{
  overall?: OverallDraftValues | null
  article?: ArticleDraftValues | null
}>()

function text(value: string | null | undefined): string {
  return value?.trim() || '—'
}
</script>

<template>
  <dl v-if="overall" class="history-value-grid">
    <div><dt>法律类别</dt><dd>{{ text(overall.lawCategory) }}</dd></div>
    <div><dt>整体关键词</dt><dd>{{ text(overall.overallKeywords) }}</dd></div>
    <div><dt>摘要</dt><dd>{{ text(overall.summary) }}</dd></div>
    <div><dt>备注</dt><dd>{{ text(overall.overallNote) }}</dd></div>
  </dl>
  <dl v-else-if="article" class="history-value-grid">
    <div><dt>条目类型</dt><dd>{{ article.itemType ? ITEM_TYPE_LABELS[article.itemType] : '—' }}</dd></div>
    <div><dt>关键词</dt><dd>{{ text(article.keywords) }}</dd></div>
    <div><dt>涉及主体</dt><dd>{{ text(article.subjects) }}</dd></div>
    <div><dt>法律责任</dt><dd>{{ text(article.legalLiability) }}</dd></div>
    <div><dt>标注备注</dt><dd>{{ text(article.annotationNote) }}</dd></div>
  </dl>
  <p v-else class="history-empty-inline">暂无标注快照</p>
</template>
