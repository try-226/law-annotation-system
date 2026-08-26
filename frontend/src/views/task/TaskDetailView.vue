<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { cancelTask, getTask, startTask } from '../../api/tasks'
import type { ValidityStatus } from '../../types/law'
import type { TaskDetail } from '../../types/task'
import { formatTaskDateTime, isCancelableTaskState, TASK_TYPE_LABELS } from '../../types/task'
import { authState } from '../../state/auth'
import { notify } from '../../state/notifications'
import { parseFailure, safeErrorMessage } from '../../utils/errors'
import CancelTaskModal from './CancelTaskModal.vue'
import TaskStatusBadge from './TaskStatusBadge.vue'

const route = useRoute()
const router = useRouter()
const task = ref<TaskDetail | null>(null)
const loading = ref(false)
const loadError = ref('')
const starting = ref(false)
const cancelOpen = ref(false)
const cancelBusy = ref(false)
const cancelError = ref('')
let requestSequence = 0

const isAdmin = computed(() => authState.user?.role === 'ADMIN')
const backRouteName = computed(() => (isAdmin.value ? 'admin-tasks' : 'my-tasks'))
const taskId = computed(() => String(route.params.taskId ?? ''))
const annotatorWorkbenchLabel = computed(() => {
  if (!task.value) return '查看'
  // 部分驳回在 PR13 只读是当前 editableScope 的能力边界，不重定义 TaskState。
  return task.value.taskState === 'ANNOTATING' ? '继续标注' : '查看标注'
})

const validityLabels: Record<ValidityStatus, string> = {
  ACTIVE: '现行有效', NOT_EFFECTIVE: '尚未生效', INVALID: '失效', REPEALED: '已废止',
}

function taskErrorMessage(error: unknown, fallback: string): string {
  return parseFailure(error).userMessage || safeErrorMessage(error, fallback)
}

async function loadTask(): Promise<void> {
  const sequence = ++requestSequence
  loading.value = true
  loadError.value = ''
  try {
    const detail = await getTask(taskId.value)
    if (sequence === requestSequence) task.value = detail
  } catch (error: unknown) {
    if (sequence === requestSequence) {
      task.value = null
      loadError.value = taskErrorMessage(error, '任务详情加载失败，请稍后重试')
    }
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

async function startCurrentTask(): Promise<void> {
  if (!task.value || starting.value || task.value.taskState !== 'PENDING_ANNOTATION') return
  starting.value = true
  try {
    task.value = await startTask(task.value.taskId)
    notify('任务已开始', 'success')
    await router.push({ name: 'annotation-workbench', params: { taskId: task.value.taskId } })
  } catch (error: unknown) {
    notify(taskErrorMessage(error, '开始任务失败，请稍后重试'), 'error')
    await loadTask()
  } finally {
    starting.value = false
  }
}

async function submitCancel(reason: string): Promise<void> {
  if (!task.value || cancelBusy.value) return
  cancelBusy.value = true
  cancelError.value = ''
  try {
    task.value = await cancelTask(task.value.taskId, { reason })
    cancelOpen.value = false
    notify('任务已取消', 'success')
  } catch (error: unknown) {
    const failure = parseFailure(error)
    const message = taskErrorMessage(error, '取消任务失败，请稍后重试')
    if (failure.status === 404 || failure.status === 409) {
      cancelOpen.value = false
      notify(message, 'error')
      await loadTask()
    } else {
      cancelError.value = message
    }
  } finally {
    cancelBusy.value = false
  }
}

watch(taskId, loadTask)
onMounted(loadTask)
</script>

<template>
  <div class="task-page">
    <RouterLink class="detail-back" :to="{ name: backRouteName }">← 返回{{ isAdmin ? '任务管理' : '我的任务' }}</RouterLink>
    <section v-if="loading" class="panel state"><span class="spinner" />正在加载任务详情…</section>
    <section v-else-if="loadError" class="panel state state--error"><p>{{ loadError }}</p><button class="button" type="button" @click="loadTask">重新加载</button></section>
    <template v-else-if="task">
      <section class="panel detail-hero">
        <div><div class="detail-title-row"><h1>{{ task.taskName }}</h1><TaskStatusBadge :state="task.taskState" /></div><p class="detail-meta">{{ TASK_TYPE_LABELS[task.taskType] }} · {{ task.lawBaseInfoSnapshot.name }} · {{ task.annotatorName }}</p></div>
        <div class="detail-actions">
          <button v-if="!isAdmin && task.taskState === 'PENDING_ANNOTATION'" class="button button--primary" type="button" :disabled="starting" @click="startCurrentTask"><span v-if="starting" class="spinner" />{{ starting ? '开始中…' : '开始标注' }}</button>
          <RouterLink v-else-if="!isAdmin" class="button button--primary" :to="{ name: 'annotation-workbench', params: { taskId: task.taskId } }">{{ annotatorWorkbenchLabel }}</RouterLink>
          <RouterLink
            v-if="isAdmin && (task.taskState === 'PENDING_REVIEW' || task.taskState === 'PENDING_REREVIEW')"
            class="button button--primary"
            :to="{ name: 'review-workbench', params: { taskId: task.taskId } }"
          >进入{{ task.taskState === 'PENDING_REREVIEW' ? '复审' : '初审' }}工作台</RouterLink>
          <button v-if="isAdmin && isCancelableTaskState(task.taskState)" class="button button--danger" type="button" @click="cancelOpen = true">取消任务</button>
        </div>
      </section>

      <div class="detail-grid">
        <section class="panel detail-card"><h2>任务信息</h2><dl class="definition-grid"><div><dt>任务类型</dt><dd>{{ TASK_TYPE_LABELS[task.taskType] }}</dd></div><div><dt>标注员</dt><dd>{{ task.annotatorName }}</dd></div><div><dt>创建时间</dt><dd>{{ formatTaskDateTime(task.createdAt) }}</dd></div><div><dt>更新时间</dt><dd>{{ formatTaskDateTime(task.updatedAt) }}</dd></div><div><dt>绑定内容版本</dt><dd>C{{ task.contentVersionSnapshot.seq }}</dd></div><div><dt>快照法条数量</dt><dd>{{ task.contentVersionSnapshot.articles.length }} 条</dd></div></dl></section>
        <section class="panel detail-card"><h2>法律快照</h2><dl class="definition-grid"><div><dt>法律名称</dt><dd>{{ task.lawBaseInfoSnapshot.name }}</dd></div><div><dt>发布机关</dt><dd>{{ task.lawBaseInfoSnapshot.issuingAuthority }}</dd></div><div><dt>发布日期</dt><dd>{{ task.lawBaseInfoSnapshot.publicationDate }}</dd></div><div><dt>效力状态</dt><dd>{{ validityLabels[task.lawBaseInfoSnapshot.validityStatus] }}</dd></div><div><dt>结构节点</dt><dd>{{ task.structureSnapshot.length }} 个</dd></div><div><dt>内容版本ID</dt><dd>{{ task.contentVersionId }}</dd></div></dl></section>
        <section class="panel detail-card detail-card--full"><h2>任务备注</h2><p class="remark-copy">{{ task.remark || '无' }}</p></section>
        <section v-if="task.taskState === 'CANCELED'" class="panel detail-card detail-card--full"><h2>取消信息</h2><div class="cancel-summary"><p>{{ task.cancelReason || '未提供取消原因' }}</p><dl class="definition-grid"><div><dt>取消时间</dt><dd>{{ formatTaskDateTime(task.canceledAt) }}</dd></div></dl></div></section>
      </div>

      <CancelTaskModal :open="cancelOpen" :task="task" :busy="cancelBusy" :server-error="cancelError" @close="cancelOpen = false" @confirm="submitCancel" />
    </template>
  </div>
</template>

<style src="./task.css"></style>
