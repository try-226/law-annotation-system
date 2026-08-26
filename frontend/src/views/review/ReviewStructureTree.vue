<script setup lang="ts">
import { computed } from 'vue'

import type { TaskArticleSnapshot, TaskStructureNodeSnapshot } from '../../types/task'
import type { ReviewDetail, ReviewItem, ReviewTarget } from '../../types/review'
import {
  buildReviewItemMap,
  reviewTargetKey,
  searchReviewArticles,
} from './reviewState'

type TreeRow =
  | { kind: 'node'; key: string; node: TaskStructureNodeSnapshot; depth: number }
  | { kind: 'article'; key: string; article: TaskArticleSnapshot; depth: number }

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
const searchResults = computed(() => searchReviewArticles(
  props.review.contentVersionSnapshot.articles,
  props.appliedSearch,
))

const rows = computed<TreeRow[]>(() => {
  const nodes = props.review.structureSnapshot
  const articleById = new Map(props.review.contentVersionSnapshot.articles.map((article) => [article.articleId, article]))
  const children = new Map<string | null, TaskStructureNodeSnapshot[]>()
  for (const node of nodes) {
    const list = children.get(node.parentNodeId) ?? []
    list.push(node)
    children.set(node.parentNodeId, list)
  }
  const result: TreeRow[] = []
  const included = new Set<string>()
  const sortedNodes = (items: TaskStructureNodeSnapshot[]) => [...items].sort((left, right) => left.order - right.order)
  const visit = (node: TaskStructureNodeSnapshot, depth: number) => {
    result.push({ kind: 'node', key: `node:${node.nodeId}`, node, depth })
    const nodeArticles = node.articleIds
      .map((articleId) => articleById.get(articleId))
      .filter((article): article is TaskArticleSnapshot => Boolean(article))
      .sort((left, right) => left.order - right.order)
    for (const article of nodeArticles) {
      const articleId = article.articleId
      if (included.has(articleId)) continue
      included.add(articleId)
      result.push({ kind: 'article', key: `article:${articleId}`, article, depth: depth + 1 })
    }
    for (const child of sortedNodes(children.get(node.nodeId) ?? [])) visit(child, depth + 1)
  }
  for (const root of sortedNodes(children.get(null) ?? [])) visit(root, 0)
  for (const article of [...props.review.contentVersionSnapshot.articles].sort((left, right) => left.order - right.order)) {
    if (!included.has(article.articleId)) result.push({ kind: 'article', key: `article:${article.articleId}`, article, depth: 0 })
  }
  return result
})

function itemFor(target: ReviewTarget): ReviewItem | undefined {
  return itemMap.value.get(reviewTargetKey(target))
}

function stateLabel(item: ReviewItem | undefined): string {
  if (!item) return props.review.roundType === 'REREVIEW' ? '非本轮范围' : '未纳入'
  if (item.state === 'CHECKED') return '无问题'
  if (item.state === 'NEEDS_CHANGE') return '有问题'
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
      <strong>审核进度 {{ review.progress.reviewed }} / {{ review.progress.total }}</strong>
      <div class="review-progress-track"><span :style="{ width: `${review.progress.total ? (review.progress.reviewed / review.progress.total) * 100 : 0}%` }" /></div>
      <dl>
        <div><dt>未审核</dt><dd>{{ review.progress.unreviewed }}</dd></div>
        <div><dt>有问题</dt><dd>{{ review.progress.needsChange }}</dd></div>
      </dl>
      <small>进度来自服务器当前审核轮次</small>
    </footer>
  </aside>
</template>
