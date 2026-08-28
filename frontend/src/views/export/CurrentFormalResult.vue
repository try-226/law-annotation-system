<script setup lang="ts">
import { computed } from 'vue'

import type { AnnotationVersionHistory } from '../../types/history'
import type { LawArticle, LawDetail } from '../../types/law'
import { ITEM_TYPE_LABELS } from '../../types/annotation'
import { formalAvailability } from './exportDownload'

const props = defineProps<{
  law: LawDetail
  currentAnnotationVersionId: string | null
  annotation: AnnotationVersionHistory | null
  articles: LawArticle[]
  loading: boolean
  error: string
  focusedArticleId: string
}>()

const availability = computed(() => formalAvailability(
  props.law,
  props.currentAnnotationVersionId,
  props.annotation,
  Boolean(props.error),
))
const articleValues = computed(() => new Map(
  (props.annotation?.articleResults ?? []).map((item) => [item.articleId, item.values]),
))

function text(value: string | null | undefined): string {
  return value?.trim() || '—'
}

function itemTypeLabel(articleId: string): string {
  const itemType = articleValues.value.get(articleId)?.itemType
  return itemType ? ITEM_TYPE_LABELS[itemType] : '—'
}

function formatDateTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
  })
}
</script>

<template>
  <section id="formal-results" class="card formal-results-card">
    <div class="row-head formal-results-heading">
      <div><h2>当前正式结果</h2><p class="muted">只读展示当前正式 A 与其绑定的语义 C</p></div>
      <span v-if="annotation && availability.available" class="badge">A{{ annotation.seq }}</span>
    </div>
    <div v-if="loading" class="empty">正在加载正式结果…</div>
    <p v-else-if="error" class="error">{{ error }}</p>
    <template v-else-if="!availability.available">
      <p class="notice">{{ availability.message }}</p>
      <RouterLink class="button secondary small" :to="{ name: 'law-history', params: { lawId: law.id } }">从历史记录查看旧版本</RouterLink>
    </template>
    <template v-else-if="annotation">
      <p class="formal-pairing-note">A{{ annotation.seq }} → C{{ law.currentContentVersionSeq }} · {{ availability.message }}</p>
      <dl class="formal-result-meta">
        <div><dt>最终审核人 ID</dt><dd>{{ annotation.approvedBy }}</dd></div>
        <div><dt>最终审核时间</dt><dd>{{ formatDateTime(annotation.approvedAt) }}</dd></div>
        <div><dt>来源任务 ID</dt><dd>{{ annotation.sourceTaskId }}</dd></div>
      </dl>
      <section class="formal-overall-result">
        <h3>整体标注</h3>
        <dl class="formal-value-grid">
          <div><dt>法律类别</dt><dd>{{ text(annotation.overallResult.lawCategory) }}</dd></div>
          <div><dt>整体关键词</dt><dd>{{ text(annotation.overallResult.overallKeywords) }}</dd></div>
          <div><dt>摘要</dt><dd>{{ text(annotation.overallResult.summary) }}</dd></div>
          <div><dt>备注</dt><dd>{{ text(annotation.overallResult.overallNote) }}</dd></div>
        </dl>
      </section>
      <section class="formal-article-results">
        <h3>法条标注</h3>
        <p v-if="articles.length === 0" class="muted">暂无法条结果</p>
        <template v-else>
          <details
            v-for="article in articles"
            :id="`formal-article-${article.articleId}`"
            :key="article.articleId"
            :open="focusedArticleId === article.articleId"
            :class="{ 'locator-highlight': focusedArticleId === article.articleId }"
          >
            <summary><strong>{{ article.number }}</strong><span>{{ text(articleValues.get(article.articleId)?.keywords) }}</span></summary>
            <p class="formal-article-body">{{ article.body }}</p>
            <dl class="formal-value-grid">
              <div><dt>条目类型</dt><dd>{{ itemTypeLabel(article.articleId) }}</dd></div>
              <div><dt>关键词</dt><dd>{{ text(articleValues.get(article.articleId)?.keywords) }}</dd></div>
              <div><dt>涉及主体</dt><dd>{{ text(articleValues.get(article.articleId)?.subjects) }}</dd></div>
              <div><dt>法律责任</dt><dd>{{ text(articleValues.get(article.articleId)?.legalLiability) }}</dd></div>
              <div><dt>标注备注</dt><dd>{{ text(articleValues.get(article.articleId)?.annotationNote) }}</dd></div>
            </dl>
          </details>
        </template>
      </section>
    </template>
  </section>
</template>

<style src="./export.css"></style>
