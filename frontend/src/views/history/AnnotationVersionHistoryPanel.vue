<script setup lang="ts">
import { computed } from 'vue'

import type { AnnotationVersionHistory, ContentVersionHistory } from '../../types/history'
import { formatTaskDateTime } from '../../types/task'
import HistoryAnnotationValues from './HistoryAnnotationValues.vue'
import { annotationArticleRows } from './historyPresentation'

const props = defineProps<{ detail: AnnotationVersionHistory; content: ContentVersionHistory }>()
const rows = computed(() => annotationArticleRows(props.detail.articleResults, props.content.semanticArticlesSnapshot))
</script>

<template>
  <div class="history-detail-stack">
    <section class="panel history-detail-card">
      <div class="history-detail-heading"><div><span class="history-kicker">正式标注快照</span><h1>标注版本 A{{ detail.seq }}</h1></div><span class="history-readonly-badge">只读</span></div>
      <dl class="history-definition-grid"><div><dt>绑定内容版本</dt><dd>C{{ content.seq }}（{{ detail.contentVersionId }}）</dd></div><div><dt>批准人 ID</dt><dd>{{ detail.approvedBy }}</dd></div><div><dt>批准时间</dt><dd>{{ formatTaskDateTime(detail.approvedAt) }}</dd></div><div><dt>来源任务 ID</dt><dd>{{ detail.sourceTaskId }}</dd></div><div><dt>来源提交 ID</dt><dd>{{ detail.sourceSubmissionId }}</dd></div><div><dt>历史法条结果</dt><dd>{{ rows.length }} 条</dd></div></dl>
    </section>
    <section class="panel history-detail-card"><h2>整体标注结果</h2><HistoryAnnotationValues :overall="detail.overallResult" /></section>
    <section class="panel history-detail-card"><h2>法条标注结果</h2><p v-if="rows.length === 0" class="history-empty-inline">该版本没有法条标注结果</p><div v-else class="history-article-list"><details v-for="row in rows" :key="row.articleId"><summary><strong>{{ row.number }}</strong><span>{{ row.articleId }}</span></summary><div class="history-article-body">{{ row.body }}</div><HistoryAnnotationValues :article="row.values" /></details></div></section>
  </div>
</template>
