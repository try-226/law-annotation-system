<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { getLawHistory } from '../../api/history'
import type { HistoryCategory, LawHistory } from '../../types/history'
import { formatTaskDateTime } from '../../types/task'
import { parseFailure, safeErrorMessage } from '../../utils/errors'
import { isRetryableFailure } from '../../utils/failurePolicy'
import { historyTimelineRows } from './historyPresentation'

const route = useRoute()
const lawId = computed(() => String(route.params.lawId ?? ''))
const history = ref<LawHistory | null>(null)
const loading = ref(false)
const loadError = ref('')
const retryable = ref(false)
let requestSequence = 0

const categoryLabels: Record<HistoryCategory, string> = {
  CONTENT_VERSION: '内容版本', LAW_AUDIT: '法律审计', ANNOTATION_VERSION: '标注版本',
  TASK: '任务', SUBMISSION: '提交', REVIEW: '审核', CANCELLATION: '取消',
}
const timeline = computed(() => history.value
  ? historyTimelineRows(lawId.value, history.value.timeline)
  : [])
const backRoute = computed(() => {
  if (loadError.value) return { name: 'law-list' }
  return history.value?.deleted
    ? { name: 'law-recycle' }
    : { name: 'law-detail', params: { lawId: lawId.value } }
})
const backLabel = computed(() => {
  if (loadError.value) return '法律管理'
  return history.value?.deleted ? '回收站' : '法律详情'
})

function errorMessage(error: unknown): string {
  const failure = parseFailure(error)
  if (failure.status === 403) return '无权访问该法律历史记录'
  if (failure.status === 404) return '法律历史记录不存在或不可访问'
  if (failure.code === 'HISTORY.SNAPSHOT_INCONSISTENT') return '历史快照数据不一致，暂无法展示'
  return failure.userMessage || safeErrorMessage(error, '历史记录加载失败')
}

async function loadHistory(): Promise<void> {
  const sequence = ++requestSequence
  loading.value = true
  loadError.value = ''
  retryable.value = false
  try {
    const response = await getLawHistory(lawId.value)
    if (sequence === requestSequence) history.value = response
  } catch (error: unknown) {
    if (sequence === requestSequence) {
      history.value = null
      const failure = parseFailure(error)
      loadError.value = errorMessage(error)
      retryable.value = isRetryableFailure(failure)
    }
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

watch(lawId, () => void loadHistory(), { immediate: true })
</script>

<template>
  <div class="history-page">
    <RouterLink class="history-back" :to="backRoute">← 返回{{ backLabel }}</RouterLink>
    <section v-if="loading" class="panel history-state"><span class="spinner" />正在加载历史记录…</section>
    <section v-else-if="loadError" class="panel history-state history-state--error"><p>{{ loadError }}</p><button v-if="retryable" class="button" type="button" @click="loadHistory">重新加载</button></section>
    <template v-else-if="history">
      <header class="panel history-hero"><div><span class="history-kicker">法律 ID：{{ history.lawId }}</span><h1>法律历史记录</h1><p>统一展示内容版本、标注版本、任务、提交、审核与取消历史；顺序以服务器返回为准。</p></div><span class="history-readonly-badge">只读</span></header>
      <p v-if="history.deleted" class="history-warning">该法律已进入回收站<span v-if="history.deletedAt">，删除时间：{{ formatTaskDateTime(history.deletedAt) }}</span>。历史快照仍可读取。</p>
      <section v-if="timeline.length === 0" class="panel history-state"><div class="history-empty-icon">◎</div><p>暂无历史记录</p></section>
      <ol v-else class="history-timeline">
        <li v-for="item in timeline" :key="item.eventId" :class="`history-category--${item.category.toLowerCase()}`"><span class="history-timeline-dot" /><article class="panel"><div class="history-timeline-time">{{ formatTaskDateTime(item.occurredAt) }}</div><div class="history-timeline-content"><div><span class="history-category-label">{{ categoryLabels[item.category] }}</span><h2>{{ item.summary }}</h2><p>事件类型：{{ item.type }}</p><small v-if="item.actorId">操作者 ID：{{ item.actorId }}</small></div><RouterLink v-if="item.route" class="button" :to="item.route">查看详情</RouterLink><span v-else class="history-unavailable">详情类型暂不支持</span></div></article></li>
      </ol>
    </template>
  </div>
</template>

<style src="./history.css"></style>
