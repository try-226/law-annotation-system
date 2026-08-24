<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import type { PageResponse } from '../../api/types'
import { listTasks, startTask } from '../../api/tasks'
import type { TaskListItem, TaskState } from '../../types/task'
import { ANNOTATOR_TASK_ACTION_LABELS, formatTaskDateTime, TASK_STATE_LABELS, TASK_TYPE_LABELS } from '../../types/task'
import { notify } from '../../state/notifications'
import { parseFailure, safeErrorMessage } from '../../utils/errors'
import { trimText, validateSearch } from '../../utils/validation'
import TaskStatusBadge from './TaskStatusBadge.vue'

const PAGE_SIZE = 10
const router = useRouter()
const searchInput = ref('')
const appliedSearch = ref('')
const stateFilter = ref<'' | TaskState>('')
const searchError = ref('')
const currentPage = ref(1)
const result = ref<PageResponse<TaskListItem>>({ items: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 })
const loading = ref(false)
const listError = ref('')
const startingTaskId = ref('')
let requestSequence = 0

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
      state: stateFilter.value || undefined,
      page: currentPage.value - 1,
      size: PAGE_SIZE,
    })
    if (sequence === requestSequence) result.value = page
  } catch (error: unknown) {
    if (sequence === requestSequence) listError.value = taskErrorMessage(error, '我的任务加载失败，请稍后重试')
  } finally {
    if (sequence === requestSequence) loading.value = false
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

async function start(task: TaskListItem): Promise<void> {
  if (startingTaskId.value || task.taskState !== 'PENDING_ANNOTATION') return
  startingTaskId.value = task.taskId
  try {
    await startTask(task.taskId)
    await loadTasks()
    notify('任务已开始', 'success')
    await router.push({ name: 'my-task-detail', params: { taskId: task.taskId } })
  } catch (error: unknown) {
    notify(taskErrorMessage(error, '开始任务失败，请稍后重试'), 'error')
    await loadTasks()
  } finally {
    startingTaskId.value = ''
  }
}

watch(stateFilter, () => {
  currentPage.value = 1
  void loadTasks()
})

onMounted(loadTasks)
</script>

<template>
  <div class="task-page">
    <header class="page-heading"><h1>我的任务</h1><p>查看并处理分配给你的标注任务</p></header>

    <section class="panel task-filters">
      <form class="task-search" @submit.prevent="applySearch"><input v-model="searchInput" class="input" maxlength="100" placeholder="按任务名称搜索" aria-label="搜索我的任务" /><button class="button button--primary" type="submit">搜索</button></form>
      <div class="status-filters" aria-label="任务状态筛选">
        <button class="status-filter" :class="{ active: stateFilter === '' }" type="button" @click="stateFilter = ''">全部</button>
        <button v-for="(label, state) in TASK_STATE_LABELS" :key="state" class="status-filter" :class="{ active: stateFilter === state }" type="button" @click="stateFilter = state">{{ label }}</button>
      </div>
      <p v-if="searchError" class="field-error filter-error">{{ searchError }}</p>
    </section>

    <section class="panel table-panel">
      <div v-if="loading" class="state"><span class="spinner" />正在加载我的任务…</div>
      <div v-else-if="listError" class="state state--error"><p>{{ listError }}</p><button class="button" type="button" @click="loadTasks">重新加载</button></div>
      <div v-else-if="result.items.length === 0" class="state"><div class="empty-icon">◎</div><p>暂无符合条件的任务</p></div>
      <div v-else class="table-scroll">
        <table>
          <thead><tr><th>任务名称</th><th>任务类型</th><th>法律名称</th><th>任务状态</th><th>创建时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="task in result.items" :key="task.taskId">
              <td><strong>{{ task.taskName }}</strong><small v-if="task.remark" class="secondary-copy">有任务备注</small></td><td>{{ TASK_TYPE_LABELS[task.taskType] }}</td><td>{{ task.lawName }}</td>
              <td><TaskStatusBadge :state="task.taskState" /></td><td>{{ formatTaskDateTime(task.createdAt) }}</td>
              <td class="actions">
                <button v-if="task.taskState === 'PENDING_ANNOTATION'" class="button button--text" type="button" :disabled="Boolean(startingTaskId)" @click="start(task)"><span v-if="startingTaskId === task.taskId" class="spinner" />{{ startingTaskId === task.taskId ? '开始中…' : ANNOTATOR_TASK_ACTION_LABELS[task.taskState] }}</button>
                <RouterLink v-else class="button button--text" :to="{ name: 'my-task-detail', params: { taskId: task.taskId } }">{{ ANNOTATOR_TASK_ACTION_LABELS[task.taskState] }}</RouterLink>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer v-if="!loading && !listError && result.totalElements > 0" class="task-pagination"><span>共 {{ result.totalElements }} 条</span><div><button class="button" type="button" :disabled="currentPage <= 1" @click="goToPage(currentPage - 1)">上一页</button><span>第 {{ currentPage }} / {{ Math.max(result.totalPages, 1) }} 页</span><button class="button" type="button" :disabled="currentPage >= result.totalPages" @click="goToPage(currentPage + 1)">下一页</button></div></footer>
    </section>
  </div>
</template>

<style src="./task.css"></style>
