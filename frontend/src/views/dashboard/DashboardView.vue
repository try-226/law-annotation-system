<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { getDashboardSummary, getDashboardTodos } from '../../api/dashboard'
import type { DashboardSummary, DashboardTodos } from '../../types/dashboard'
import { formatTaskDateTime, TASK_TYPE_LABELS } from '../../types/task'
import { parseFailure, safeErrorMessage } from '../../utils/errors'
import { isRetryableFailure } from '../../utils/failurePolicy'
import TaskStatusBadge from '../task/TaskStatusBadge.vue'
import { dashboardCards } from './dashboardPresentation'

const summary = ref<DashboardSummary | null>(null)
const summaryLoading = ref(false)
const summaryError = ref('')
const summaryRetryable = ref(false)
const todos = ref<DashboardTodos | null>(null)
const todosLoading = ref(false)
const todosError = ref('')
const todosRetryable = ref(false)
let summarySequence = 0
let todosSequence = 0

const cards = computed(() => summary.value ? dashboardCards(summary.value) : [])

function dashboardError(error: unknown, fallback: string): string {
  return parseFailure(error).userMessage || safeErrorMessage(error, fallback)
}

async function loadSummary(): Promise<void> {
  const sequence = ++summarySequence
  summaryLoading.value = true
  summaryError.value = ''
  summaryRetryable.value = false
  try {
    const response = await getDashboardSummary()
    if (sequence === summarySequence) summary.value = response
  } catch (error: unknown) {
    if (sequence === summarySequence) {
      summary.value = null
      const failure = parseFailure(error)
      summaryError.value = dashboardError(error, '工作台统计加载失败')
      summaryRetryable.value = isRetryableFailure(failure)
    }
  } finally {
    if (sequence === summarySequence) summaryLoading.value = false
  }
}

async function loadTodos(): Promise<void> {
  const sequence = ++todosSequence
  todosLoading.value = true
  todosError.value = ''
  todosRetryable.value = false
  try {
    const response = await getDashboardTodos()
    if (sequence === todosSequence) todos.value = response
  } catch (error: unknown) {
    if (sequence === todosSequence) {
      todos.value = null
      const failure = parseFailure(error)
      todosError.value = dashboardError(error, '工作台待办加载失败')
      todosRetryable.value = isRetryableFailure(failure)
    }
  } finally {
    if (sequence === todosSequence) todosLoading.value = false
  }
}

onMounted(() => {
  void loadSummary()
  void loadTodos()
})
</script>

<template>
  <div class="dashboard-page">
    <header class="page-heading dashboard-heading">
      <div><h1>管理员工作台</h1><p>查看当前法律、任务与审核待办概览</p></div>
      <div class="dashboard-heading-actions">
        <RouterLink class="button button--primary" :to="{ name: 'admin-tasks' }">创建 / 管理任务</RouterLink>
        <RouterLink class="button" :to="{ name: 'law-import' }">导入法律</RouterLink>
      </div>
    </header>

    <section aria-labelledby="dashboard-summary-title">
      <div class="dashboard-section-heading"><h2 id="dashboard-summary-title">统计概览</h2></div>
      <div v-if="summaryLoading" class="panel dashboard-state"><span class="spinner" />正在加载统计信息…</div>
      <div v-else-if="summaryError" class="panel dashboard-state dashboard-state--error">
        <p>{{ summaryError }}</p>
        <button v-if="summaryRetryable" class="button" type="button" @click="loadSummary">重新加载</button>
      </div>
      <div v-else class="dashboard-card-grid">
        <RouterLink v-for="card in cards" :key="card.key" class="panel dashboard-card" :to="{ name: card.routeName }">
          <span>{{ card.label }}</span><strong>{{ card.value.toLocaleString('zh-CN') }}</strong><small>查看</small>
        </RouterLink>
      </div>
    </section>

    <section class="dashboard-todos" aria-labelledby="dashboard-todos-title">
      <div class="dashboard-section-heading">
        <h2 id="dashboard-todos-title">审核待办</h2>
        <RouterLink :to="{ name: 'admin-tasks' }">查看全部任务</RouterLink>
      </div>
      <div v-if="todosLoading" class="panel dashboard-state"><span class="spinner" />正在加载审核待办…</div>
      <div v-else-if="todosError" class="panel dashboard-state dashboard-state--error">
        <p>{{ todosError }}</p>
        <button v-if="todosRetryable" class="button" type="button" @click="loadTodos">重新加载</button>
      </div>
      <div v-else-if="todos" class="dashboard-todo-grid">
        <section class="panel dashboard-todo-panel">
          <header><div><h3>待审核</h3><p>等待管理员开始或继续初审</p></div><span>{{ todos.pendingReview.length }}</span></header>
          <div v-if="todos.pendingReview.length === 0" class="dashboard-empty">暂无待审核任务</div>
          <ul v-else class="dashboard-todo-list">
            <li v-for="todo in todos.pendingReview" :key="todo.taskId">
              <div><strong>{{ todo.taskName }}</strong><span>{{ todo.lawName }} · {{ TASK_TYPE_LABELS[todo.taskType] }}</span><small>更新于 {{ formatTaskDateTime(todo.updatedAt) }}</small></div>
              <div class="dashboard-todo-action"><TaskStatusBadge :state="todo.taskState" /><RouterLink :to="{ name: 'review-workbench', params: { taskId: todo.taskId } }">进入审核</RouterLink></div>
            </li>
          </ul>
        </section>

        <section class="panel dashboard-todo-panel">
          <header><div><h3>待复审</h3><p>等待管理员处理复审轮次</p></div><span>{{ todos.pendingRereview.length }}</span></header>
          <div v-if="todos.pendingRereview.length === 0" class="dashboard-empty">暂无待复审任务</div>
          <ul v-else class="dashboard-todo-list">
            <li v-for="todo in todos.pendingRereview" :key="todo.taskId">
              <div><strong>{{ todo.taskName }}</strong><span>{{ todo.lawName }} · {{ TASK_TYPE_LABELS[todo.taskType] }}</span><small>更新于 {{ formatTaskDateTime(todo.updatedAt) }}</small></div>
              <div class="dashboard-todo-action"><TaskStatusBadge :state="todo.taskState" /><RouterLink :to="{ name: 'review-workbench', params: { taskId: todo.taskId } }">进入复审</RouterLink></div>
            </li>
          </ul>
        </section>
      </div>
    </section>

    <section class="panel dashboard-shortcuts" aria-labelledby="dashboard-shortcuts-title">
      <h2 id="dashboard-shortcuts-title">快捷入口</h2>
      <div>
        <RouterLink :to="{ name: 'law-import' }"><strong>导入法律</strong><span>录入或粘贴一部完整法律文件</span></RouterLink>
        <RouterLink :to="{ name: 'admin-tasks' }"><strong>创建 / 管理任务</strong><span>进入现有普通与修订任务管理</span></RouterLink>
        <RouterLink :to="{ name: 'law-list' }"><strong>法律管理</strong><span>查看法律、字段配置与回收站</span></RouterLink>
      </div>
    </section>
  </div>
</template>

<style scoped>
.dashboard-page { display: grid; gap: 26px; }
.dashboard-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; margin-bottom: 0; }
.dashboard-heading-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 10px; }
.dashboard-section-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 14px; }
.dashboard-section-heading h2, .dashboard-shortcuts h2 { margin: 0; font-size: 18px; }
.dashboard-section-heading a { color: #2868c7; font-size: 13px; }
.dashboard-card-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; }
.dashboard-card { position: relative; display: grid; min-height: 132px; gap: 12px; padding: 20px; transition: border-color .15s, transform .15s; }
.dashboard-card:hover { border-color: #9dbde8; transform: translateY(-1px); }
.dashboard-card span { color: #687386; font-size: 13px; }.dashboard-card strong { font-size: 29px; }.dashboard-card small { position: absolute; top: 18px; right: 18px; color: #2868c7; }
.dashboard-state { display: grid; min-height: 160px; place-items: center; align-content: center; gap: 12px; color: #788395; }.dashboard-state p { margin: 0; }.dashboard-state--error { color: #a63c3c; }
.dashboard-todo-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.dashboard-todo-panel { overflow: hidden; }.dashboard-todo-panel > header { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; border-bottom: 1px solid #e8ecf1; padding: 18px 20px; }
.dashboard-todo-panel h3 { margin: 0 0 5px; font-size: 16px; }.dashboard-todo-panel header p { margin: 0; color: #7d8797; font-size: 12px; }.dashboard-todo-panel header > span { display: grid; width: 30px; height: 30px; place-items: center; border-radius: 50%; background: #edf4ff; color: #2868c7; font-weight: 700; }
.dashboard-empty { display: grid; min-height: 150px; place-items: center; color: #8992a1; font-size: 13px; }
.dashboard-todo-list { margin: 0; padding: 0; list-style: none; }.dashboard-todo-list li { display: flex; align-items: center; justify-content: space-between; gap: 16px; border-bottom: 1px solid #edf0f4; padding: 15px 20px; }.dashboard-todo-list li:last-child { border-bottom: 0; }
.dashboard-todo-list li > div:first-child { display: grid; min-width: 0; gap: 4px; }.dashboard-todo-list strong { overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }.dashboard-todo-list span, .dashboard-todo-list small { color: #768194; font-size: 12px; }
.dashboard-todo-action { display: grid; flex: none; justify-items: end; gap: 6px; }.dashboard-todo-action a { color: #2868c7; font-size: 12px; }
.dashboard-shortcuts { padding: 20px; }.dashboard-shortcuts > div { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin-top: 15px; }.dashboard-shortcuts a { display: grid; gap: 6px; border: 1px solid #e0e6ee; border-radius: 7px; padding: 15px; }.dashboard-shortcuts a:hover { border-color: #a9c6ec; background: #f7faff; }.dashboard-shortcuts strong { font-size: 14px; }.dashboard-shortcuts span { color: #768194; font-size: 12px; line-height: 1.55; }
.dashboard-page a:focus-visible, .dashboard-page button:focus-visible { outline: 3px solid rgb(40 104 199 / 28%); outline-offset: 2px; }
@media (max-width: 1100px) { .dashboard-card-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }.dashboard-todo-grid { grid-template-columns: 1fr; } }
@media (max-width: 680px) { .dashboard-heading { flex-direction: column; }.dashboard-heading-actions { justify-content: flex-start; }.dashboard-card-grid, .dashboard-shortcuts > div { grid-template-columns: 1fr; }.dashboard-todo-list li { align-items: flex-start; }.dashboard-todo-action { justify-items: start; } }
</style>
