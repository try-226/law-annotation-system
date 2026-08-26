<script setup lang="ts">
import { computed } from 'vue'

import type { ReviewDetail, ReviewItem, ReviewTarget } from '../../types/review'
import {
  buildReviewArticleProgress,
  buildReviewDirectoryRows,
  buildReviewItemMap,
  reviewTargetKey,
  searchReviewArticles,
} from './reviewState'

const props = defineProps<{
  review: ReviewDetail
  selected: ReviewTarget
  searchInput: string
  appliedSearch: string
  disabled: boolean
}>()

const emit = defineEmits<{
  target: [target: ReviewTarget]
  'update:searchInput': [value: string]
  search: []
  clearSearch: []
}>()

const itemMap = computed(() => buildReviewItemMap(props.review.items))
const overallItem = computed(() => itemFor({ kind: 'overall' }))
const articleProgress = computed(() => buildReviewArticleProgress(props.review.items))
const searchResults = computed(() => searchReviewArticles(
  props.review.contentVersionSnapshot.articles,
  props.appliedSearch,
))

const rows = computed(() => buildReviewDirectoryRows(props.review))

function itemFor(target: ReviewTarget): ReviewItem | undefined {
  return itemMap.value.get(reviewTargetKey(target))
}

function stateLabel(item: ReviewItem | undefined): string {
  if (!item) return props.review.roundType === 'REREVIEW' ? '非本轮范围' : '未纳入'
  if (item.state === 'CHECKED') return '已核查'
  if (item.state === 'NEEDS_CHANGE') return '待修改'
  return '未审核'
}

function stateClass(item: ReviewItem | undefined): string {
  if (!item) return 'out-of-scope'
  return item.state.toLowerCase().replace('_', '-')
}

function selectedTarget(target: ReviewTarget): boolean {
  return reviewTargetKey(props.selected) === reviewTargetKey(target)
}
</script>

<template>
  <aside class="review-sidebar panel">
    <header><h2>审核目录</h2><span>第 {{ review.roundNo }} 轮</span></header>
    <form class="review-search" @submit.prevent="emit('search')">
      <input
        :value="searchInput"
        class="input"
        maxlength="100"
        placeholder="搜索条号 / 正文"
        aria-label="搜索审核内容"
        @input="emit('update:searchInput', ($event.target as HTMLInputElement).value)"
      />
      <div class="review-search-actions">
        <button class="button button--primary" type="submit">搜索</button>
        <button v-if="appliedSearch" class="button" type="button" @click="emit('clearSearch')">清除</button>
      </div>
    </form>

    <div v-if="appliedSearch" class="review-tree">
      <div class="review-results-heading"><strong>搜索结果</strong><span>{{ searchResults.length }} 项</span></div>
      <p v-if="searchResults.length === 0" class="review-empty-copy">冻结提交中没有匹配的法条</p>
      <button
        v-for="article in searchResults"
        :key="article.articleId"
        type="button"
        class="review-tree-target review-search-result"
        :disabled="disabled"
        :class="{ selected: selectedTarget({ kind: 'article', articleId: article.articleId }) }"
        @click="emit('target', { kind: 'article', articleId: article.articleId })"
      >
        <span><strong>{{ article.number }}</strong><small>{{ article.body }}</small></span>
        <em :class="stateClass(itemFor({ kind: 'article', articleId: article.articleId }))">
          {{ stateLabel(itemFor({ kind: 'article', articleId: article.articleId })) }}
        </em>
      </button>
    </div>

    <div v-else class="review-tree">
      <button
        type="button"
        class="review-tree-target review-overall-target"
        :disabled="disabled"
        :class="{ selected: selectedTarget({ kind: 'overall' }) }"
        @click="emit('target', { kind: 'overall' })"
      >
        <span>整体信息</span>
        <em :class="stateClass(itemFor({ kind: 'overall' }))">{{ stateLabel(itemFor({ kind: 'overall' })) }}</em>
      </button>
      <template v-for="row in rows" :key="row.key">
        <div v-if="row.kind === 'node'" class="review-tree-node" :style="{ paddingLeft: `${10 + row.depth * 15}px` }">
          {{ row.node.title }}
        </div>
        <button
          v-else
          type="button"
          class="review-tree-target"
          :disabled="disabled"
          :class="{ selected: selectedTarget({ kind: 'article', articleId: row.article.articleId }) }"
          :style="{ paddingLeft: `${18 + row.depth * 15}px` }"
          @click="emit('target', { kind: 'article', articleId: row.article.articleId })"
        >
          <span>{{ row.article.number }}</span>
          <em :class="stateClass(itemFor({ kind: 'article', articleId: row.article.articleId }))">
            {{ stateLabel(itemFor({ kind: 'article', articleId: row.article.articleId })) }}
          </em>
        </button>
      </template>
    </div>

    <footer class="review-progress">
      <div class="review-overall-progress">
        <strong>整体信息</strong>
        <em :class="stateClass(overallItem)">{{ stateLabel(overallItem) }}</em>
      </div>
      <strong>法条审核进度 {{ articleProgress.processed }} / {{ articleProgress.total }}</strong>
      <div class="review-progress-track"><span :style="{ width: `${articleProgress.total ? (articleProgress.processed / articleProgress.total) * 100 : 0}%` }" /></div>
      <dl>
        <div><dt>已核查</dt><dd>{{ articleProgress.checked }}</dd></div>
        <div><dt>待修改</dt><dd>{{ articleProgress.needsChange }}</dd></div>
        <div><dt>未审核</dt><dd>{{ articleProgress.unreviewed }}</dd></div>
      </dl>
      <small>仅统计服务器本轮 scope 内的法条；完成条件仍以服务器总进度为准</small>
    </footer>
  </aside>
</template>
