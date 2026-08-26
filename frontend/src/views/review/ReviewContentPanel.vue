<script setup lang="ts">
import { computed } from 'vue'

import type { ValidityStatus } from '../../types/law'
import type { TaskArticleSnapshot } from '../../types/task'
import type { ReviewDetail, ReviewFieldRow, ReviewItem, ReviewTarget } from '../../types/review'
import { buildReviewFieldRows, buildReviewItemMap, reviewTargetKey } from './reviewState'

const props = defineProps<{
  review: ReviewDetail
  target: ReviewTarget
  article: TaskArticleSnapshot | null
  structurePath: string
}>()

const item = computed<ReviewItem | null>(() => buildReviewItemMap(props.review.items).get(reviewTargetKey(props.target)) ?? null)
const comparisonMode = computed(() => props.review.roundType === 'REREVIEW')

const overallRows = computed<ReviewFieldRow[]>(() => buildReviewFieldRows(
  'overall',
  props.review.before?.overall ?? null,
  props.review.after.overall,
))

const articleRows = computed<ReviewFieldRow[]>(() => {
  if (props.target.kind !== 'article') return []
  return buildReviewFieldRows(
    'article',
    props.review.before?.articles[props.target.articleId] ?? null,
    props.review.after.articles[props.target.articleId] ?? null,
  )
})

const validityLabels: Record<ValidityStatus, string> = {
  ACTIVE: '现行有效',
  NOT_EFFECTIVE: '尚未生效',
  INVALID: '失效',
  REPEALED: '已废止',
}

function statusLabel(): string {
  if (!item.value) return '非本轮复审范围'
  if (item.value.state === 'CHECKED') return '无问题'
  if (item.value.state === 'NEEDS_CHANGE') return '有问题'
  return '未审核'
}

function required(kind: 'overall' | 'article', key: string): boolean {
  return props.review.fieldConfigSnapshot[kind].some((field) => field.fieldKey === key && field.required)
}
</script>

<template>
  <div class="review-content">
    <header class="review-content-heading">
      <div>
        <h2>{{ target.kind === 'overall' ? '整体信息' : (article?.number ?? '法条不存在') }}</h2>
        <p v-if="target.kind === 'article'">章节路径：{{ structurePath || '未归入结构节点' }}</p>
        <p v-else>审核任务创建时冻结的法律基础信息与整体标注</p>
      </div>
      <span class="review-state-pill" :class="item?.state.toLowerCase().replace('_', '-') || 'out-of-scope'">{{ statusLabel() }}</span>
    </header>

    <p v-if="!item && review.roundType === 'REREVIEW'" class="review-scope-banner">
      此项不属于本轮必审范围，可以查看；当前审核人如发现遗漏，可将其新增为问题。
    </p>
    <p v-if="comparisonMode && !review.before" class="review-warning-banner">
      当前复审轮次没有可用的修改前提交，无法展示字段级对照。
    </p>

    <template v-if="target.kind === 'overall'">
      <dl class="review-law-meta">
        <div><dt>法律名称</dt><dd>{{ review.lawBaseInfoSnapshot.name }}</dd></div>
        <div><dt>发布机关</dt><dd>{{ review.lawBaseInfoSnapshot.issuingAuthority }}</dd></div>
        <div><dt>发布日期</dt><dd>{{ review.lawBaseInfoSnapshot.publicationDate }}</dd></div>
        <div><dt>效力状态</dt><dd>{{ validityLabels[review.lawBaseInfoSnapshot.validityStatus] }}</dd></div>
      </dl>
      <section class="review-fields">
        <h3>{{ comparisonMode ? '整体标注修改对照' : '提交的整体标注' }}</h3>
        <div class="review-field-list">
          <article v-for="row in overallRows" :key="row.key" class="review-field-card" :class="{ changed: comparisonMode && row.changed, unchanged: comparisonMode && !row.changed }">
            <header><strong>{{ row.label }}</strong><span v-if="required('overall', row.key)">必填</span><em v-if="comparisonMode">{{ row.changed ? '已修改' : '未修改' }}</em></header>
            <div v-if="comparisonMode" class="review-compare-grid"><div><small>修改前</small><p>{{ row.before }}</p></div><div><small>修改后</small><p>{{ row.after }}</p></div></div>
            <p v-else class="review-single-value">{{ row.after }}</p>
          </article>
        </div>
      </section>
    </template>

    <template v-else-if="article">
      <section class="review-article-body"><p>{{ article.body }}</p></section>
      <section class="review-fields">
        <h3>{{ comparisonMode ? '法条标注修改对照' : '提交的法条标注' }}</h3>
        <div class="review-field-list">
          <article v-for="row in articleRows" :key="row.key" class="review-field-card" :class="{ changed: comparisonMode && row.changed, unchanged: comparisonMode && !row.changed }">
            <header><strong>{{ row.label }}</strong><span v-if="required('article', row.key)">必填</span><em v-if="comparisonMode">{{ row.changed ? '已修改' : '未修改' }}</em></header>
            <div v-if="comparisonMode" class="review-compare-grid"><div><small>修改前</small><p>{{ row.before }}</p></div><div><small>修改后</small><p>{{ row.after }}</p></div></div>
            <p v-else class="review-single-value">{{ row.after }}</p>
          </article>
        </div>
      </section>
    </template>
    <section v-else class="review-content-missing">当前冻结提交中找不到此法条，请刷新后重试。</section>

    <section v-if="item?.issue" class="review-issue-summary">
      <strong>问题原因</strong>
      <p>{{ item.issue.reason }}</p>
      <small>记录时间：{{ new Date(item.issue.createdAt).toLocaleString('zh-CN', { hour12: false }) }}</small>
    </section>
  </div>
</template>
