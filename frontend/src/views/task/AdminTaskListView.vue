<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

import type { PageResponse, User } from '../../api/types'
import { cancelTask, listTasks } from '../../api/tasks'
import { listUsers } from '../../api/users'
import type { TaskDetail, TaskListItem, TaskState, TaskType } from '../../types/task'
import { formatTaskDateTime, isCancelableTaskState, TASK_STATE_LABELS, TASK_TYPE_LABELS } from '../../types/task'
import { notify } from '../../state/notifications'
import { parseFailure, safeErrorMessage } from '../../utils/errors'
import { trimText, validateSearch } from '../../utils/validation'
import CancelTaskModal from './CancelTaskModal.vue'
import CreateOrdinaryTaskModal from './CreateOrdinaryTaskModal.vue'
import CreateRevisionTaskModal from '../revision/CreateRevisionTaskModal.vue'
import TaskStatusBadge from './TaskStatusBadge.vue'

const PAGE_SIZE = 10
const searchInput = ref('')
const appliedSearch = ref('')
const typeFilter = ref<'' | TaskType>('')
const stateFilter = ref<'' | TaskState>('')
const annotatorFilter = ref('')
const searchError = ref('')
const currentPage = ref(1)
const result = ref<PageResponse<TaskListItem>>({ items: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 })
const loading = ref(false)
const listError = ref('')
let requestSequence = 0

const annotators = ref<User[]>([])
const annotatorsError = ref('')
const ordinaryCreateOpen = ref(false)
const revisionCreateOpen = ref(false)
const cancelTarget = ref<TaskListItem | null>(null)
const cancelBusy = ref(false)
const cancelError = ref('')

function taskErrorMessage(error: unknown, fallback: string): string {
  return parseFailure(error).userMessage || safeErrorMessage(error, fallback)
}

async function loadTasks(): Promise<void> {
  const sequence = ++requestSequence
  loading.value = true
  listError.value = ''
  try {
    const page = await listTasks({
      taskName: appliedSearch.value || undefined,
      taskType: typeFilter.value || undefined,
      annotatorId: annotatorFilter.value || undefined,
      state: stateFilter.value || undefined,
      page: currentPage.value - 1,
      size: PAGE_SIZE,
    })
    if (sequence === requestSequence) result.value = page
  } catch (error: unknown) {
    if (sequence === requestSequence) listError.value = taskErrorMessage(error, '任务列表加载失败，请稍后重试')
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

async function loadAnnotators(): Promise<void> {
  annotatorsError.value = ''
  try {
    const collected: User[] = []
    let page = 0
    let totalPages = 1
    while (page < totalPages) {
      const response = await listUsers({ role: 'ANNOTATOR', page, size: 100 })
      collected.push(...response.items)
      totalPages = response.totalPages
      page += 1
    }
    annotators.value = collected
  } catch (error: unknown) {
    annotatorsError.value = taskErrorMessage(error, '标注员筛选数据加载失败')
  }
}

async function applySearch(): Promise<void> {
  searchError.value = validateSearch(searchInput.value) ?? ''
  if (searchError.value) return
  appliedSearch.value = trimText(searchInput.value)
  currentPage.value = 1
  await loadTasks()
}

async function goToPage(page: number): Promise<void> {
  if (page < 1 || page > result.value.totalPages || page === currentPage.value) return
  currentPage.value = page
  await loadTasks()
}

function openCancel(task: TaskListItem): void {
  if (!isCancelableTaskState(task.taskState)) return
  cancelTarget.value = task
  cancelError.value = ''
}

function closeCancel(): void {
  if (cancelBusy.value) return
  cancelTarget.value = null
  cancelError.value = ''
}

async function submitCancel(reason: string): Promise<void> {
  if (!cancelTarget.value || cancelBusy.value) return
  cancelBusy.value = true
  cancelError.value = ''
  try {
    await cancelTask(cancelTarget.value.taskId, { reason })
    cancelTarget.value = null
    await loadTasks()
    if (result.value.items.length === 0 && currentPage.value > 1) {
      currentPage.value -= 1
      await loadTasks()
    }
    notify('任务已取消', 'success')
  } catch (error: unknown) {
    const failure = parseFailure(error)
    const message = taskErrorMessage(error, '取消任务失败，请稍后重试')
    if (failure.status === 404 || failure.status === 409) {
      cancelTarget.value = null
      await loadTasks()
      notify(message, 'error')
    } else {
      cancelError.value = message
    }
  } finally {
    cancelBusy.value = false
  }
}

async function handleOrdinaryCreated(): Promise<void> {
  ordinaryCreateOpen.value = false
  currentPage.value = 1
  await loadTasks()
  notify('普通任务创建成功', 'success')
}

async function handleRevisionCreated(task: TaskDetail): Promise<void> {
  revisionCreateOpen.value = false
  currentPage.value = 1
  await loadTasks()
  const scope = task.revisionScope
  const modeLabel = scope?.mode === 'CONTENT_CHANGE' ? '正文变化型' : '标注修正型'
  const scopeCount = scope?.articleIds.length ?? 0
  notify(`修订任务创建成功（${modeLabel}，范围法条 ${scopeCount} 条）`, 'success')
}

watch([typeFilter, stateFilter, annotatorFilter], () => {
  currentPage.value = 1
  void loadTasks()
})

onMounted(() => {
  void loadTasks()
  void loadAnnotators()
})
</script>

<template>
  <div class="task-page">
    <header class="page-heading heading-row">
      <div><h1>任务管理</h1><p>查看和管理普通标注与修订任务</p></div>
      <div class="task-create-actions">
        <button class="button" type="button" @click="ordinaryCreateOpen = true">＋ 创建普通任务</button>
        <button class="button button--primary" type="button" @click="revisionCreateOpen = true">＋ 创建修订任务</button>
      </div>
    </header>

    <section class="panel task-filters">
      <form class="task-search" @submit.prevent="applySearch">
        <input v-model="searchInput" class="input" maxlength="100" placeholder="按任务名称搜索" aria-label="搜索任务" />
        <button class="button button--primary" type="submit">搜索</button>
      </form>
      <div class="filter-item"><label for="task-type-filter">任务类型</label><select id="task-type-filter" v-model="typeFilter" class="select"><option value="">全部类型</option><option v-for="(label, type) in TASK_TYPE_LABELS" :key="type" :value="type">{{ label }}</option></select></div>
      <div class="filter-item"><label for="task-state-filter">任务状态</label><select id="task-state-filter" v-model="stateFilter" class="select"><option value="">全部状态</option><option v-for="(label, state) in TASK_STATE_LABELS" :key="state" :value="state">{{ label }}</option></select></div>
      <div class="filter-item"><label for="task-annotator-filter">标注员</label><select id="task-annotator-filter" v-model="annotatorFilter" class="select"><option value="">全部标注员</option><option v-for="annotator in annotators" :key="annotator.id" :value="annotator.id">{{ annotator.name }}{{ annotator.enabled ? '' : '（已停用）' }}</option></select></div>
      <p v-if="searchError" class="field-error filter-error">{{ searchError }}</p>
    </section>
    <p v-if="annotatorsError" class="filter-note">{{ annotatorsError }}，任务列表仍可使用其他条件筛选。</p>

    <section class="panel table-panel">
      <div v-if="loading" class="state"><span class="spinner" />正在加载任务…</div>
      <div v-else-if="listError" class="state state--error"><p>{{ listError }}</p><button class="button" type="button" @click="loadTasks">重新加载</button></div>
      <div v-else-if="result.items.length === 0" class="state"><div class="empty-icon">◎</div><p>暂无符合条件的任务</p></div>
      <div v-else class="table-scroll">
        <table>
          <thead><tr><th>任务名称</th><th>法律名称</th><th>任务类型</th><th>标注员</th><th>任务状态</th><th>创建时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="task in result.items" :key="task.taskId">
              <td><strong>{{ task.taskName }}</strong><small v-if="task.remark" class="secondary-copy" :title="task.remark">有任务备注</small></td>
              <td>{{ task.lawName }}</td><td>{{ TASK_TYPE_LABELS[task.taskType] }}</td><td>{{ task.annotatorName }}</td>
              <td><TaskStatusBadge :state="task.taskState" /></td><td>{{ formatTaskDateTime(task.createdAt) }}</td>
              <td class="actions">
                <RouterLink class="button button--text" :to="{ name: 'admin-task-detail', params: { taskId: task.taskId } }">详情</RouterLink>
                <RouterLink
                  v-if="task.taskState === 'PENDING_REVIEW' || task.taskState === 'PENDING_REREVIEW'"
                  class="button button--text"
                  :to="{ name: 'review-workbench', params: { taskId: task.taskId } }"
                >{{ task.taskState === 'PENDING_REREVIEW' ? '复审' : '审核' }}</RouterLink>
                <button v-if="isCancelableTaskState(task.taskState)" class="button button--text button--text-danger" type="button" @click="openCancel(task)">取消</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer v-if="!loading && !listError && result.totalElements > 0" class="task-pagination">
        <span>共 {{ result.totalElements }} 条</span>
        <div><button class="button" type="button" :disabled="currentPage <= 1" @click="goToPage(currentPage - 1)">上一页</button><span>第 {{ currentPage }} / {{ Math.max(result.totalPages, 1) }} 页</span><button class="button" type="button" :disabled="currentPage >= result.totalPages" @click="goToPage(currentPage + 1)">下一页</button></div>
      </footer>
    </section>

    <CreateOrdinaryTaskModal :open="ordinaryCreateOpen" @close="ordinaryCreateOpen = false" @created="handleOrdinaryCreated" />
    <CreateRevisionTaskModal :open="revisionCreateOpen" @close="revisionCreateOpen = false" @created="handleRevisionCreated" />
    <CancelTaskModal :open="Boolean(cancelTarget)" :task="cancelTarget" :busy="cancelBusy" :server-error="cancelError" @close="closeCancel" @confirm="submitCancel" />
  </div>
</template>

<style src="./task.css"></style>
