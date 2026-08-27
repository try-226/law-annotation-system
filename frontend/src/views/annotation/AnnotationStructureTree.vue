<script setup lang="ts">
import { computed } from 'vue'

import {
  SEARCH_SCOPE_LABELS,
  type AnnotationSearchPage,
  type AnnotationSearchScope,
  type AnnotationTarget,
  type TaskDraftResponse,
} from '../../types/annotation'
import type { TaskDetail } from '../../types/task'
import { orderedTaskStructureRows, revisionTargetStatus, sameTarget } from './annotationDraftState'

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

const rows = computed(() => orderedTaskStructureRows(props.task))
const targetStatus = (target: AnnotationTarget) => revisionTargetStatus(props.task, target, props.draft)
</script>

<template>
  <aside class="annotation-sidebar panel">
    <header><h2>{{ task.taskType === 'REVISION' ? '修订目录' : '标注目录' }}</h2><span>草稿版本 {{ draft.revision }}</span></header>
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
        <strong>{{ item.target.kind === 'overall' ? '整体信息' : item.articleNumber }}<em v-if="targetStatus(item.target)">{{ targetStatus(item.target) }}</em></strong>
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
        <span>整体信息</span><small v-if="task.taskType === 'REVISION'" :class="{ editable: targetStatus({ kind: 'overall' }) === '当前可修改' }">{{ targetStatus({ kind: 'overall' }) }}</small><small v-else :class="{ complete: draft.progress.overallCompleted }">{{ draft.progress.overallCompleted ? '✓ 已完成' : '未完成' }}</small>
      </button>
      <template v-for="row in rows" :key="row.key">
        <div v-if="row.kind === 'node'" class="annotation-tree-node" :style="{ paddingLeft: `${10 + row.depth * 15}px` }">{{ row.node.title }}</div>
        <button v-else type="button" class="annotation-tree-target" :class="{ selected: sameTarget(selected, { kind: 'article', articleId: row.article.articleId }) }" :style="{ paddingLeft: `${18 + row.depth * 15}px` }" @click="emit('target', { kind: 'article', articleId: row.article.articleId })">
          <span>{{ row.article.number }}</span><small v-if="task.taskType === 'REVISION'" :class="{ mandatory: task.revisionScope?.mandatoryArticleIds.includes(row.article.articleId), editable: targetStatus({ kind: 'article', articleId: row.article.articleId }) === '当前可修改' }">{{ targetStatus({ kind: 'article', articleId: row.article.articleId }) }}</small><small v-else :class="{ complete: articleCompletion[row.article.articleId] }">{{ articleCompletion[row.article.articleId] ? '✓' : '未完成' }}</small>
        </button>
      </template>
    </div>

    <footer class="annotation-progress">
      <template v-if="task.taskType === 'REVISION'">
        <strong>范围法条 {{ task.revisionScope?.articleIds.length ?? 0 }} 条</strong>
        <small>当前可编辑 {{ draft.editableScope.editableArticleIds.length }} 条；权限以服务器 editableScope 为准</small>
      </template>
      <template v-else>
        <strong>法条进度 {{ draft.progress.filledArticles }} / {{ draft.progress.totalArticles }}</strong>
        <div class="annotation-progress-track"><span :style="{ width: `${draft.progress.totalArticles ? (draft.progress.filledArticles / draft.progress.totalArticles) * 100 : 0}%` }" /></div>
        <small>汇总进度来自服务器已保存草稿</small>
      </template>
    </footer>
  </aside>
</template>
