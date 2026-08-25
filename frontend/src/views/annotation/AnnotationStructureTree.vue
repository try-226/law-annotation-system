<script setup lang="ts">
import { computed } from 'vue'

import {
  SEARCH_SCOPE_LABELS,
  type AnnotationSearchPage,
  type AnnotationSearchScope,
  type AnnotationTarget,
  type TaskDraftResponse,
} from '../../types/annotation'
import type { TaskArticleSnapshot, TaskDetail, TaskStructureNodeSnapshot } from '../../types/task'
import { sameTarget } from './annotationDraftState'

type TreeRow =
  | { kind: 'node'; key: string; node: TaskStructureNodeSnapshot; depth: number }
  | { kind: 'article'; key: string; article: TaskArticleSnapshot; depth: number }

const props = defineProps<{
  task: TaskDetail
  draft: TaskDraftResponse
  selected: AnnotationTarget
  articleCompletion: Record<string, boolean>
  searchInput: string
  searchScope: AnnotationSearchScope
  searchResult: AnnotationSearchPage
}>()

const emit = defineEmits<{
  target: [target: AnnotationTarget]
  'update:searchInput': [value: string]
  'update:searchScope': [value: AnnotationSearchScope]
  search: []
  page: [page: number]
}>()

const rows = computed<TreeRow[]>(() => {
  const nodes = props.task.structureSnapshot
  const articleById = new Map(props.task.contentVersionSnapshot.articles.map((article) => [article.articleId, article]))
  const children = new Map<string | null, TaskStructureNodeSnapshot[]>()
  for (const node of nodes) {
    const list = children.get(node.parentNodeId) ?? []
    list.push(node)
    children.set(node.parentNodeId, list)
  }
  const sortNodes = (items: TaskStructureNodeSnapshot[]) => [...items].sort((left, right) => left.order - right.order)
  const result: TreeRow[] = []
  const includedArticles = new Set<string>()
  const visit = (node: TaskStructureNodeSnapshot, depth: number) => {
    result.push({ kind: 'node', key: `node:${node.nodeId}`, node, depth })
    for (const articleId of node.articleIds) {
      const article = articleById.get(articleId)
      if (!article || includedArticles.has(articleId)) continue
      includedArticles.add(articleId)
      result.push({ kind: 'article', key: `article:${articleId}`, article, depth: depth + 1 })
    }
    for (const child of sortNodes(children.get(node.nodeId) ?? [])) visit(child, depth + 1)
  }
  for (const root of sortNodes(children.get(null) ?? [])) visit(root, 0)
  for (const article of [...props.task.contentVersionSnapshot.articles].sort((left, right) => left.order - right.order)) {
    if (!includedArticles.has(article.articleId)) {
      result.push({ kind: 'article', key: `article:${article.articleId}`, article, depth: 0 })
    }
  }
  return result
})
</script>

<template>
  <aside class="annotation-sidebar panel">
    <header><h2>标注目录</h2><span>草稿版本 {{ draft.revision }}</span></header>
    <form class="annotation-search" @submit.prevent="emit('search')">
      <input :value="searchInput" class="input" maxlength="100" placeholder="搜索当前任务" aria-label="任务内搜索" @input="emit('update:searchInput', ($event.target as HTMLInputElement).value)" />
      <div class="annotation-search-row">
        <select :value="searchScope" class="select" aria-label="搜索范围" @change="emit('update:searchScope', ($event.target as HTMLSelectElement).value as AnnotationSearchScope)">
          <option v-for="(label, value) in SEARCH_SCOPE_LABELS" :key="value" :value="value">{{ label }}</option>
        </select>
        <button class="button button--primary" type="submit">搜索</button>
      </div>
      <p v-if="searchResult.error" class="field-error">{{ searchResult.error }}</p>
    </form>

    <div v-if="searchResult.active && !searchResult.error" class="annotation-search-results">
      <div class="annotation-results-heading"><strong>搜索结果</strong><span>{{ searchResult.totalElements }} 项</span></div>
      <p v-if="searchResult.items.length === 0" class="annotation-empty-copy">当前任务中没有匹配内容</p>
      <button v-for="item in searchResult.items" :key="item.key" type="button" class="annotation-search-result" @click="emit('target', item.target)">
        <strong>{{ item.target.kind === 'overall' ? '整体信息' : item.articleNumber }}</strong>
        <small>{{ item.lawName }} · {{ item.structurePath }} · {{ item.fieldLabel }}</small>
        <span><template v-for="(segment, index) in item.segments" :key="index"><mark v-if="segment.highlighted">{{ segment.text }}</mark><template v-else>{{ segment.text }}</template></template></span>
      </button>
      <footer v-if="searchResult.totalPages > 1" class="annotation-result-pages">
        <button type="button" :disabled="searchResult.page <= 1" @click="emit('page', searchResult.page - 1)">上一页</button>
        <span>{{ searchResult.page }} / {{ searchResult.totalPages }}</span>
        <button type="button" :disabled="searchResult.page >= searchResult.totalPages" @click="emit('page', searchResult.page + 1)">下一页</button>
      </footer>
    </div>

    <div v-else class="annotation-tree">
      <button type="button" class="annotation-tree-target annotation-overall-target" :class="{ selected: sameTarget(selected, { kind: 'overall' }) }" @click="emit('target', { kind: 'overall' })">
        <span>整体信息</span><small :class="{ complete: draft.progress.overallCompleted }">{{ draft.progress.overallCompleted ? '✓ 已完成' : '未完成' }}</small>
      </button>
      <template v-for="row in rows" :key="row.key">
        <div v-if="row.kind === 'node'" class="annotation-tree-node" :style="{ paddingLeft: `${10 + row.depth * 15}px` }">{{ row.node.title }}</div>
        <button v-else type="button" class="annotation-tree-target" :class="{ selected: sameTarget(selected, { kind: 'article', articleId: row.article.articleId }) }" :style="{ paddingLeft: `${18 + row.depth * 15}px` }" @click="emit('target', { kind: 'article', articleId: row.article.articleId })">
          <span>{{ row.article.number }}</span><small :class="{ complete: articleCompletion[row.article.articleId] }">{{ articleCompletion[row.article.articleId] ? '✓' : '未完成' }}</small>
        </button>
      </template>
    </div>

    <footer class="annotation-progress">
      <strong>法条进度 {{ draft.progress.filledArticles }} / {{ draft.progress.totalArticles }}</strong>
      <div class="annotation-progress-track"><span :style="{ width: `${draft.progress.totalArticles ? (draft.progress.filledArticles / draft.progress.totalArticles) * 100 : 0}%` }" /></div>
      <small>汇总进度来自服务器已保存草稿</small>
    </footer>
  </aside>
</template>
