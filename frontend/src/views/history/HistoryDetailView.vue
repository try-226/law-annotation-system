<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import {
  getAnnotationVersionHistory,
  getContentVersionHistory,
  getLawAuditHistory,
  getTaskHistory,
} from '../../api/history'
import { authState } from '../../state/auth'
import type {
  AnnotationVersionHistory,
  ContentVersionHistory,
  LawAuditHistory,
  TaskHistory,
} from '../../types/history'
import { parseFailure, safeErrorMessage } from '../../utils/errors'
import { isRetryableFailure } from '../../utils/failurePolicy'
import AnnotationVersionHistoryPanel from './AnnotationVersionHistoryPanel.vue'
import ContentVersionHistoryPanel from './ContentVersionHistoryPanel.vue'
import LawAuditHistoryPanel from './LawAuditHistoryPanel.vue'
import TaskHistoryPanel from './TaskHistoryPanel.vue'
import { isLawHistoryReturnContext, taskHistoryBackRoute } from './historyPresentation'

type DetailKind = 'CONTENT_VERSION' | 'ANNOTATION_VERSION' | 'LAW_AUDIT' | 'TASK'

const route = useRoute()
const lawId = computed(() => String(route.params.lawId ?? ''))
const kind = computed<DetailKind | null>(() => {
  if (route.name === 'history-content-version') return 'CONTENT_VERSION'
  if (route.name === 'history-annotation-version') return 'ANNOTATION_VERSION'
  if (route.name === 'history-law-audit') return 'LAW_AUDIT'
  if (route.name === 'task-history') return 'TASK'
  return null
})
const content = ref<ContentVersionHistory | null>(null)
const annotation = ref<AnnotationVersionHistory | null>(null)
const audit = ref<LawAuditHistory | null>(null)
const task = ref<TaskHistory | null>(null)
const loading = ref(false)
const loadError = ref('')
const retryable = ref(false)
let requestSequence = 0

const taskId = computed(() => String(route.params.taskId ?? ''))
const returnsToLawHistory = computed(() => kind.value === 'TASK'
  && isLawHistoryReturnContext(authState.user?.role, route.query.from))
const backRoute = computed(() => {
  if (kind.value !== 'TASK') return { name: 'law-history', params: { lawId: lawId.value } }
  if (returnsToLawHistory.value) {
    return taskHistoryBackRoute(authState.user?.role, lawId.value, taskId.value, route.query.from)
  }
  if (loadError.value) return { name: authState.user?.role === 'ADMIN' ? 'admin-tasks' : 'my-tasks' }
  return taskHistoryBackRoute(authState.user?.role, lawId.value, taskId.value, route.query.from)
})
const backLabel = computed(() => kind.value === 'TASK'
  ? (returnsToLawHistory.value
      ? '法律历史'
      : loadError.value
        ? (authState.user?.role === 'ADMIN' ? '任务管理' : '我的任务')
        : '任务详情')
  : '历史记录')

function resetDetail(): void {
  content.value = null
  annotation.value = null
  audit.value = null
  task.value = null
}

function errorMessage(error: unknown): string {
  const failure = parseFailure(error)
  if (failure.status === 403) return '无权访问该历史记录'
  if (failure.status === 404) return kind.value === 'TASK'
    ? '任务历史不存在或不可访问'
    : '历史记录不存在或不可访问'
  if (failure.code === 'HISTORY.SNAPSHOT_INCONSISTENT') return '历史快照数据不一致，暂无法展示'
  return failure.userMessage || safeErrorMessage(error, '历史详情加载失败')
}

async function loadDetail(): Promise<void> {
  const sequence = ++requestSequence
  loading.value = true
  loadError.value = ''
  retryable.value = false
  resetDetail()
  try {
    if (kind.value === 'CONTENT_VERSION') {
      const response = await getContentVersionHistory(lawId.value, String(route.params.contentVersionId ?? ''))
      if (sequence === requestSequence) content.value = response
    } else if (kind.value === 'ANNOTATION_VERSION') {
      const annotationResponse = await getAnnotationVersionHistory(
        lawId.value,
        String(route.params.annotationVersionId ?? ''),
      )
      if (sequence !== requestSequence) return
      const contentResponse = await getContentVersionHistory(lawId.value, annotationResponse.contentVersionId)
      if (sequence !== requestSequence) return
      annotation.value = annotationResponse
      content.value = contentResponse
    } else if (kind.value === 'LAW_AUDIT') {
      const response = await getLawAuditHistory(lawId.value, String(route.params.auditId ?? ''))
      if (sequence === requestSequence) audit.value = response
    } else if (kind.value === 'TASK') {
      const response = await getTaskHistory(lawId.value, taskId.value)
      if (sequence === requestSequence) task.value = response
    } else {
      throw new Error('不支持的历史详情路由')
    }
  } catch (error: unknown) {
    if (sequence === requestSequence) {
      resetDetail()
      const failure = parseFailure(error)
      loadError.value = errorMessage(error)
      retryable.value = isRetryableFailure(failure)
    }
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

watch(() => route.fullPath, () => void loadDetail(), { immediate: true })
</script>

<template>
  <div class="history-page">
    <RouterLink class="history-back" :to="backRoute">← 返回{{ backLabel }}</RouterLink>
    <section v-if="loading" class="panel history-state"><span class="spinner" />正在加载历史详情…</section>
    <section v-else-if="loadError" class="panel history-state history-state--error"><p>{{ loadError }}</p><button v-if="retryable" class="button" type="button" @click="loadDetail">重新加载</button></section>
    <ContentVersionHistoryPanel v-else-if="kind === 'CONTENT_VERSION' && content" :detail="content" />
    <AnnotationVersionHistoryPanel v-else-if="kind === 'ANNOTATION_VERSION' && annotation && content" :detail="annotation" :content="content" />
    <LawAuditHistoryPanel v-else-if="kind === 'LAW_AUDIT' && audit" :detail="audit" />
    <TaskHistoryPanel v-else-if="kind === 'TASK' && task" :detail="task" />
    <section v-else class="panel history-state history-state--error"><p>历史详情暂无法展示</p></section>
  </div>
</template>

<style src="./history.css"></style>
