<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'

import {
  clearArticleDraft,
  clearOverallDraft,
  getTaskDraft,
  saveArticleDraft,
  saveOverallDraft,
  submitTaskForReview,
} from '../../api/annotation'
import { getTask, startTask } from '../../api/tasks'
import { notify } from '../../state/notifications'
import type {
  AnnotationSearchScope,
  AnnotationTarget,
  ArticleDraftForm,
  OverallDraftForm,
  SubmissionLocator,
  TaskDraftResponse,
} from '../../types/annotation'
import type { TaskArticleSnapshot, TaskDetail } from '../../types/task'
import { parseFailure, safeErrorMessage } from '../../utils/errors'
import TaskStatusBadge from '../task/TaskStatusBadge.vue'
import AnnotationStructureTree from './AnnotationStructureTree.vue'
import ArticleAnnotationPanel from './ArticleAnnotationPanel.vue'
import ClearAnnotationModal from './ClearAnnotationModal.vue'
import OverallAnnotationPanel from './OverallAnnotationPanel.vue'
import SubmitAnnotationModal from './SubmitAnnotationModal.vue'
import UnsavedChangesModal from './UnsavedChangesModal.vue'
import {
  createArticleForm,
  createOverallForm,
  decideAnnotationNavigation,
  formsEqual,
  isArticleDraftComplete,
  isTargetEditable,
  parseAnnotationLocator,
  reconcileSavedForm,
  sameWorkbenchSession,
  sameTarget,
  selectInitialTarget,
  type WorkbenchSessionIdentity,
} from './annotationDraftState'
import { searchTask } from './annotationSearch'
import {
  normalizeArticlePayload,
  normalizeOverallPayload,
  validateArticleDraft,
  validateOverallDraft,
} from './annotationValidation'

const route = useRoute()
const router = useRouter()
const taskId = computed(() => String(route.params.taskId ?? ''))
const task = ref<TaskDetail | null>(null)
const draft = ref<TaskDraftResponse | null>(null)
const selected = ref<AnnotationTarget>({ kind: 'overall' })
const overallForm = ref<OverallDraftForm>(createOverallForm(null))
const overallBaseline = ref<OverallDraftForm>(createOverallForm(null))
const articleForm = ref<ArticleDraftForm>(createArticleForm(null))
const articleBaseline = ref<ArticleDraftForm>(createArticleForm(null))
const formErrors = ref<Record<string, string>>({})
const loading = ref(false)
const loadError = ref('')
const starting = ref(false)
const saving = ref(false)
const clearing = ref(false)
const submitting = ref(false)
const clearOpen = ref(false)
const clearTaskId = ref('')
const submitOpen = ref(false)
const submitTaskId = ref('')
const submitError = ref('')
const submitLocators = ref<SubmissionLocator[]>([])
const unsavedMode = ref<'switch' | 'leave' | null>(null)
const pendingTarget = ref<AnnotationTarget | null>(null)
const pendingFocusFieldKey = ref('')
const pendingLeavePath = ref('')
const searchInput = ref('')
const appliedSearch = ref('')
const searchScope = ref<AnnotationSearchScope>('ALL')
const searchPage = ref(1)
let workbenchGeneration = 0
let allowNextLeave = false

const selectedArticle = computed<TaskArticleSnapshot | null>(() => {
  const detail = task.value
  const target = selected.value
  if (!detail || target.kind !== 'article') return null
  return detail.contentVersionSnapshot.articles.find((item) => item.articleId === target.articleId) ?? null
})

const articleCompletion = computed<Record<string, boolean>>(() => {
  if (!task.value || !draft.value) return {}
  return Object.fromEntries(task.value.contentVersionSnapshot.articles.map((article) => [
    article.articleId,
    isArticleDraftComplete(task.value!.fieldConfigSnapshot.article, draft.value!.articleDrafts[article.articleId]),
  ]))
})

const currentEditable = computed(() => Boolean(draft.value && isTargetEditable(selected.value, draft.value)))
const canSubmit = computed(() => Boolean(draft.value?.editableScope.overallEditable))
const dirty = computed(() => selected.value.kind === 'overall'
  ? !formsEqual(overallBaseline.value, overallForm.value)
  : !formsEqual(articleBaseline.value, articleForm.value))

const searchResult = computed(() => {
  if (!task.value || !draft.value) {
    return { active: false, query: '', error: null, items: [], page: 1, size: 10, totalElements: 0, totalPages: 0 }
  }
  return searchTask(task.value, draft.value, {
    query: appliedSearch.value, scope: searchScope.value, page: searchPage.value, size: 10,
  })
})

const targetLabel = computed(() => selected.value.kind === 'overall'
  ? '整体信息'
  : (selectedArticle.value?.number ?? '当前法条'))

const previousTarget = computed<AnnotationTarget | null>(() => adjacentTarget(-1))
const nextTarget = computed<AnnotationTarget | null>(() => adjacentTarget(1))

function workbenchError(error: unknown, fallback: string): string {
  return parseFailure(error).userMessage || safeErrorMessage(error, fallback)
}

function structurePath(articleId: string): string {
  if (!task.value) return ''
  const byId = new Map(task.value.structureSnapshot.map((node) => [node.nodeId, node]))
  let node = task.value.structureSnapshot.find((item) => item.articleIds.includes(articleId)) ?? null
  const titles: string[] = []
  while (node) {
    if (node.title.trim()) titles.unshift(node.title.trim())
    node = node.parentNodeId ? (byId.get(node.parentNodeId) ?? null) : null
  }
  return titles.join(' / ')
}

function adjacentTarget(offset: number): AnnotationTarget | null {
  if (!task.value) return null
  const targets: AnnotationTarget[] = [
    { kind: 'overall' },
    ...[...task.value.contentVersionSnapshot.articles]
      .sort((left, right) => left.order - right.order)
      .map((article) => ({ kind: 'article' as const, articleId: article.articleId })),
  ]
  const index = targets.findIndex((target) => sameTarget(target, selected.value))
  return targets[index + offset] ?? null
}

function syncSelectedForms(preserveCurrent = false): void {
  if (!draft.value) return
  formErrors.value = {}
  if (selected.value.kind === 'overall') {
    const serverForm = createOverallForm(draft.value.overallDraft)
    overallBaseline.value = serverForm
    if (!preserveCurrent) overallForm.value = createOverallForm(draft.value.overallDraft)
  } else {
    const values = draft.value.articleDrafts[selected.value.articleId] ?? null
    articleBaseline.value = createArticleForm(values)
    if (!preserveCurrent) articleForm.value = createArticleForm(values)
  }
}

function applyTarget(target: AnnotationTarget): void {
  selected.value = target
  syncSelectedForms()
}

async function focusAnnotationField(fieldKey: string): Promise<void> {
  if (!fieldKey) return
  await nextTick()
  document.getElementById(`annotation-${fieldKey}`)?.focus()
}

function currentWorkbenchSession(): WorkbenchSessionIdentity {
  return { taskId: taskId.value, generation: workbenchGeneration }
}

function operationStillBelongsToTask(operation: WorkbenchSessionIdentity): boolean {
  return Boolean(task.value && sameWorkbenchSession(operation, currentWorkbenchSession())
    && task.value.taskId === operation.taskId)
}

function resetTaskUiState(): void {
  clearOpen.value = false
  clearTaskId.value = ''
  submitOpen.value = false
  submitTaskId.value = ''
  submitError.value = ''
  submitLocators.value = []
  unsavedMode.value = null
  pendingTarget.value = null
  pendingFocusFieldKey.value = ''
  pendingLeavePath.value = ''
  formErrors.value = {}
  searchInput.value = ''
  appliedSearch.value = ''
  searchScope.value = 'ALL'
  searchPage.value = 1
}

async function loadWorkbench(resetTarget = true): Promise<void> {
  const generation = ++workbenchGeneration
  loading.value = true
  loadError.value = ''
  try {
    const [taskResponse, draftResponse] = await Promise.all([getTask(taskId.value), getTaskDraft(taskId.value)])
    if (generation !== workbenchGeneration) return
    task.value = taskResponse
    draft.value = draftResponse
    if (resetTarget) selected.value = selectInitialTarget(taskResponse, draftResponse)
    syncSelectedForms()
  } catch (error: unknown) {
    if (generation === workbenchGeneration) {
      task.value = null
      draft.value = null
      loadError.value = workbenchError(error, '标注工作台加载失败，请稍后重试')
    }
  } finally {
    if (generation === workbenchGeneration) loading.value = false
  }
}

async function startPendingTask(): Promise<void> {
  if (!task.value || starting.value || task.value.taskState !== 'PENDING_ANNOTATION') return
  const operation = currentWorkbenchSession()
  starting.value = true
  try {
    await startTask(operation.taskId)
    if (!operationStillBelongsToTask(operation)) return
    notify('任务已开始', 'success')
    await loadWorkbench(true)
  } catch (error: unknown) {
    if (!operationStillBelongsToTask(operation)) return
    notify(workbenchError(error, '开始任务失败，请稍后重试'), 'error')
    await loadWorkbench(true)
  } finally {
    starting.value = false
  }
}

function mapFieldErrors(error: unknown): Record<string, string> {
  return Object.fromEntries(parseFailure(error).locators.map((locator) => {
    const segments = locator.path.split('.')
    return [segments.at(-1) ?? locator.path, locator.message]
  }))
}

async function handleConflict(operation: WorkbenchSessionIdentity): Promise<void> {
  if (!operationStillBelongsToTask(operation)) return
  const latestOverall = { ...overallForm.value }
  const latestArticle = { ...articleForm.value }
  notify('任务状态已经变化，本次未保存修改未写入服务器。正在重新加载任务状态。', 'error')
  try {
    const [latestTask, latestDraft] = await Promise.all([getTask(operation.taskId), getTaskDraft(operation.taskId)])
    if (!operationStillBelongsToTask(operation)) return
    task.value = latestTask
    draft.value = latestDraft
    if (isTargetEditable(selected.value, latestDraft)) {
      syncSelectedForms(true)
      if (selected.value.kind === 'overall') overallForm.value = latestOverall
      else articleForm.value = latestArticle
      notify('服务器状态已更新，本地未保存输入仍保留，请检查后重试。', 'info')
    } else {
      syncSelectedForms(false)
      notify('任务已不可编辑，刚才的本地修改没有保存成功；当前显示服务器已保存内容。', 'error')
    }
  } catch (refreshError: unknown) {
    if (!operationStillBelongsToTask(operation)) return
    overallForm.value = latestOverall
    articleForm.value = latestArticle
    notify(workbenchError(refreshError, '任务状态刷新失败，本地输入仍保留'), 'error')
  }
}

async function saveCurrent(): Promise<boolean> {
  if (!task.value || !draft.value || !currentEditable.value || saving.value) return false
  const operation = currentWorkbenchSession()
  const operationTarget = { ...selected.value } as AnnotationTarget
  const preservedOverall = { ...overallForm.value }
  const preservedArticle = { ...articleForm.value }
  const errors = selected.value.kind === 'overall'
    ? validateOverallDraft(overallForm.value)
    : validateArticleDraft(articleForm.value)
  formErrors.value = errors
  if (Object.keys(errors).length) {
    notify('请先修正当前标注中的格式问题', 'error')
    return false
  }

  saving.value = true
  try {
    const response = operationTarget.kind === 'overall'
      ? await saveOverallDraft(operation.taskId, normalizeOverallPayload(preservedOverall))
      : await saveArticleDraft(operation.taskId, operationTarget.articleId, normalizeArticlePayload(preservedArticle))
    if (!operationStillBelongsToTask(operation) || !sameTarget(selected.value, operationTarget)) return false
    draft.value = response
    formErrors.value = {}
    let retainsUnsavedChanges = false
    if (operationTarget.kind === 'overall') {
      const reconciliation = reconcileSavedForm(
        createOverallForm(response.overallDraft), preservedOverall, overallForm.value,
      )
      overallBaseline.value = reconciliation.baseline
      overallForm.value = reconciliation.current
      retainsUnsavedChanges = !formsEqual(reconciliation.baseline, reconciliation.current)
    } else {
      const reconciliation = reconcileSavedForm(
        createArticleForm(response.articleDrafts[operationTarget.articleId] ?? null), preservedArticle, articleForm.value,
      )
      articleBaseline.value = reconciliation.baseline
      articleForm.value = reconciliation.current
      retainsUnsavedChanges = !formsEqual(reconciliation.baseline, reconciliation.current)
    }
    if (retainsUnsavedChanges) {
      notify('草稿已保存；保存期间的新修改仍保留为未保存状态。', 'info')
      return false
    }
    notify(`${targetLabel.value}草稿已保存`, 'success')
    return true
  } catch (error: unknown) {
    if (!operationStillBelongsToTask(operation)) return false
    const failure = parseFailure(error)
    if (failure.status === 400) formErrors.value = mapFieldErrors(error)
    if (failure.status === 409) {
      await handleConflict(operation)
    } else {
      notify(workbenchError(error, '草稿保存失败，请稍后重试'), 'error')
    }
    return false
  } finally {
    saving.value = false
  }
}

async function confirmClear(): Promise<void> {
  if (!task.value || !draft.value || clearing.value || !currentEditable.value
    || clearTaskId.value !== task.value.taskId) return
  const operation = currentWorkbenchSession()
  const operationTarget = { ...selected.value } as AnnotationTarget
  clearing.value = true
  try {
    const response = operationTarget.kind === 'overall'
      ? await clearOverallDraft(operation.taskId)
      : await clearArticleDraft(operation.taskId, operationTarget.articleId)
    if (!operationStillBelongsToTask(operation) || !sameTarget(selected.value, operationTarget)) return
    draft.value = response
    clearOpen.value = false
    clearTaskId.value = ''
    syncSelectedForms()
    notify(`${targetLabel.value}标注已清空`, 'success')
  } catch (error: unknown) {
    if (!operationStillBelongsToTask(operation)) return
    if (parseFailure(error).status === 409) {
      await handleConflict(operation)
      clearOpen.value = false
      clearTaskId.value = ''
    } else {
      notify(workbenchError(error, '清空标注失败，请稍后重试'), 'error')
    }
  } finally {
    clearing.value = false
  }
}

function openClearModal(): void {
  if (!task.value) return
  clearTaskId.value = task.value.taskId
  clearOpen.value = true
}

function requestTarget(target: AnnotationTarget, focusFieldKey = ''): void {
  const decision = decideAnnotationNavigation(selected.value, dirty.value, {
    kind: 'target', target, focusFieldKey: focusFieldKey || undefined,
  })
  if (decision === 'confirm') {
    pendingTarget.value = target
    pendingFocusFieldKey.value = focusFieldKey
    unsavedMode.value = 'switch'
    return
  }
  if (!sameTarget(target, selected.value)) applyTarget(target)
  void focusAnnotationField(focusFieldKey)
}

async function continuePendingAction(): Promise<void> {
  const target = pendingTarget.value
  const focusFieldKey = pendingFocusFieldKey.value
  const leavePath = pendingLeavePath.value
  unsavedMode.value = null
  pendingTarget.value = null
  pendingFocusFieldKey.value = ''
  pendingLeavePath.value = ''
  if (target) {
    applyTarget(target)
    await focusAnnotationField(focusFieldKey)
  }
  if (leavePath) {
    allowNextLeave = true
    try { await router.push(leavePath) } finally { allowNextLeave = false }
  }
}

async function saveUnsavedAction(): Promise<void> {
  if (await saveCurrent()) await continuePendingAction()
}

async function discardUnsavedAction(): Promise<void> {
  syncSelectedForms()
  await continuePendingAction()
}

async function saveAndMove(target: AnnotationTarget | null): Promise<void> {
  if (!target) return
  if (dirty.value || currentEditable.value) {
    if (!(await saveCurrent())) return
  }
  applyTarget(target)
}

function runSearch(): void {
  appliedSearch.value = searchInput.value
  searchPage.value = 1
}

async function confirmSubmit(): Promise<void> {
  if (!task.value || !draft.value || submitting.value || !canSubmit.value
    || submitTaskId.value !== task.value.taskId) return
  const operation = currentWorkbenchSession()
  submitting.value = true
  submitError.value = ''
  submitLocators.value = []
  try {
    if (dirty.value && !(await saveCurrent())) return
    const result = await submitTaskForReview(operation.taskId)
    if (!operationStillBelongsToTask(operation) || !task.value || !draft.value) return
    task.value = { ...task.value, taskState: result.taskState }
    draft.value = {
      ...draft.value,
      taskState: result.taskState,
      editableScope: { overallEditable: false, editableArticleIds: [] },
    }
    syncSelectedForms()
    submitOpen.value = false
    submitTaskId.value = ''
    notify('任务已提交审核，当前工作台已转为只读', 'success')
    try {
      const [latestTask, latestDraft] = await Promise.all([getTask(operation.taskId), getTaskDraft(operation.taskId)])
      if (!operationStillBelongsToTask(operation)) return
      task.value = latestTask
      draft.value = latestDraft
      syncSelectedForms()
    } catch (refreshError: unknown) {
      if (!operationStillBelongsToTask(operation)) return
      notify(workbenchError(refreshError, '任务已提交，但最新只读数据刷新失败'), 'error')
    }
  } catch (error: unknown) {
    if (!operationStillBelongsToTask(operation)) return
    const failure = parseFailure(error)
    if (failure.status === 422) {
      submitLocators.value = failure.locators.map((locator) => ({
        ...locator, parsed: parseAnnotationLocator(locator.path),
      }))
      submitError.value = failure.userMessage ?? '整体信息或法条标注尚未完成'
    } else if (failure.status === 409) {
      await handleConflict(operation)
      submitOpen.value = false
      submitTaskId.value = ''
    } else {
      submitError.value = workbenchError(error, '任务提交失败，请稍后重试')
    }
  } finally {
    submitting.value = false
  }
}

function openSubmitModal(): void {
  if (!task.value) return
  submitTaskId.value = task.value.taskId
  submitError.value = ''
  submitLocators.value = []
  submitOpen.value = true
}

async function locateSubmissionError(locator: SubmissionLocator): Promise<void> {
  if (!locator.parsed) return
  submitOpen.value = false
  submitTaskId.value = ''
  submitLocators.value = []
  submitError.value = ''
  requestTarget(locator.parsed.target, locator.parsed.fieldKey)
}

function beforeUnload(event: BeforeUnloadEvent): void {
  if (!dirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

onBeforeRouteLeave((to) => {
  if (allowNextLeave) return true
  if (decideAnnotationNavigation(selected.value, dirty.value, { kind: 'route', path: to.fullPath }) === 'apply') return true
  pendingLeavePath.value = to.fullPath
  unsavedMode.value = 'leave'
  return false
})

onBeforeRouteUpdate((to) => {
  if (String(to.params.taskId ?? '') === taskId.value) return true
  if (allowNextLeave || decideAnnotationNavigation(selected.value, dirty.value, { kind: 'route', path: to.fullPath }) === 'apply') {
    workbenchGeneration += 1
    resetTaskUiState()
    return true
  }
  pendingLeavePath.value = to.fullPath
  unsavedMode.value = 'leave'
  return false
})

watch(taskId, () => void loadWorkbench(true))
watch(searchScope, () => { if (appliedSearch.value) searchPage.value = 1 })
watch(searchInput, (value) => {
  if (!value.trim()) {
    appliedSearch.value = ''
    searchPage.value = 1
  }
})
onMounted(() => {
  window.addEventListener('beforeunload', beforeUnload)
  void loadWorkbench(true)
})
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
</script>

<template>
  <div class="annotation-page">
    <RouterLink class="annotation-back" :to="{ name: 'my-tasks' }">← 返回我的任务</RouterLink>
    <section v-if="loading" class="panel annotation-state"><span class="spinner" />正在加载标注工作台…</section>
    <section v-else-if="loadError" class="panel annotation-state annotation-state--error"><p>{{ loadError }}</p><button class="button" type="button" @click="loadWorkbench(true)">重新加载</button></section>
    <template v-else-if="task && draft">
      <header class="annotation-page-heading">
        <div><h1>{{ task.taskName }}</h1><p>{{ task.lawBaseInfoSnapshot.name }} · {{ task.contentVersionSnapshot.articles.length }} 条法条</p></div>
        <TaskStatusBadge :state="task.taskState" />
      </header>

      <section v-if="task.taskState === 'PENDING_ANNOTATION'" class="panel annotation-pending">
        <div><h2>当前任务尚未开始</h2><p>只有你明确点击开始后，任务才会进入标注中状态。直接访问工作台不会自动开始任务。</p></div>
        <button class="button button--primary" type="button" :disabled="starting" @click="startPendingTask"><span v-if="starting" class="spinner" />{{ starting ? '开始中…' : '开始标注' }}</button>
      </section>
      <template v-else>
        <p v-if="task.taskState === 'PARTIALLY_REJECTED'" class="annotation-scope-note">当前部分驳回任务暂以只读方式展示；问题项修改能力将在后续流程开放。</p>
        <p v-else-if="!currentEditable" class="annotation-readonly-note">当前任务状态或服务器可编辑范围不允许修改，工作台以只读方式展示已保存内容。</p>
        <div class="annotation-workbench">
          <AnnotationStructureTree
            :task="task" :draft="draft" :selected="selected" :article-completion="articleCompletion"
            :search-input="searchInput" :search-scope="searchScope" :search-result="searchResult"
            @target="requestTarget" @update:search-input="searchInput = $event" @update:search-scope="searchScope = $event"
            @search="runSearch" @page="searchPage = $event"
          />
          <main class="annotation-editor panel">
            <OverallAnnotationPanel v-if="selected.kind === 'overall'" v-model="overallForm" :task="task" :errors="formErrors" :editable="currentEditable" />
            <ArticleAnnotationPanel v-else-if="selectedArticle" v-model="articleForm" :task="task" :article="selectedArticle" :structure-path="structurePath(selectedArticle.articleId)" :errors="formErrors" :editable="currentEditable" :completed="articleCompletion[selectedArticle.articleId]" />
            <footer class="annotation-actions">
              <button v-if="currentEditable" class="button button--danger" type="button" :disabled="saving || clearing || submitting" @click="openClearModal">清空当前标注</button>
              <span class="annotation-actions-spacer" />
              <button class="button" type="button" :disabled="!previousTarget || saving" @click="requestTarget(previousTarget!)">上一项</button>
              <button v-if="currentEditable" class="button" type="button" :disabled="saving || clearing || submitting" @click="saveCurrent"><span v-if="saving" class="spinner" />保存草稿</button>
              <button v-if="currentEditable && nextTarget" class="button button--primary" type="button" :disabled="saving || clearing || submitting" @click="saveAndMove(nextTarget)">保存并下一项</button>
              <button v-else-if="nextTarget" class="button" type="button" @click="requestTarget(nextTarget)">下一项</button>
              <button v-if="canSubmit" class="button" type="button" :disabled="saving || clearing || submitting" @click="openSubmitModal">提交整部任务</button>
            </footer>
          </main>
        </div>
      </template>

      <UnsavedChangesModal :open="Boolean(unsavedMode)" :mode="unsavedMode || 'switch'" :busy="saving" @close="unsavedMode = null; pendingTarget = null; pendingFocusFieldKey = ''; pendingLeavePath = ''" @save="saveUnsavedAction" @discard="discardUnsavedAction" />
      <ClearAnnotationModal :open="clearOpen" :target-label="targetLabel" :busy="clearing" @close="clearOpen = false; clearTaskId = ''" @confirm="confirmClear" />
      <SubmitAnnotationModal :open="submitOpen" :busy="submitting" :locators="submitLocators" :error="submitError" @close="submitOpen = false; submitTaskId = ''; submitLocators = []; submitError = ''" @confirm="confirmSubmit" @locate="locateSubmissionError" />
    </template>
  </div>
</template>

<style src="./annotation.css"></style>
