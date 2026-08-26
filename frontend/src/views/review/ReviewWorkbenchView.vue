<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import {
  checkReviewItem,
  completeReviewRound,
  getReview,
  issueReviewItem,
  startReview,
} from '../../api/review'
import { getTask } from '../../api/tasks'
import { authState } from '../../state/auth'
import { notify } from '../../state/notifications'
import type { TaskArticleSnapshot, TaskDetail } from '../../types/task'
import type { ReviewDetail, ReviewItem, ReviewTarget } from '../../types/review'
import { parseFailure, safeErrorMessage } from '../../utils/errors'
import TaskStatusBadge from '../task/TaskStatusBadge.vue'
import ReviewCompleteModal from './ReviewCompleteModal.vue'
import ReviewContentPanel from './ReviewContentPanel.vue'
import ReviewIssueModal from './ReviewIssueModal.vue'
import ReviewStructureTree from './ReviewStructureTree.vue'
import { readAssignedReviewWithRetry } from './reviewRecovery'
import {
  buildReviewItemMap,
  buildReviewTargetOrder,
  canCompleteReview,
  canResumeReviewCompletion,
  findNextReviewTarget,
  normalizeIssueReason,
  reviewFailureDecision,
  reviewTargetCapabilities,
  reviewTargetKey,
  selectInitialReviewTarget,
  validateIssueReason,
} from './reviewState'

const route = useRoute()
const taskId = computed(() => String(route.params.taskId ?? ''))
const task = ref<TaskDetail | null>(null)
const review = ref<ReviewDetail | null>(null)
const selected = ref<ReviewTarget>({ kind: 'overall' })
const loading = ref(false)
const loadError = ref('')
const notStarted = ref(false)
const startBusy = ref(false)
const checkBusy = ref(false)
const issueBusy = ref(false)
const completeBusy = ref(false)
const issueOpen = ref(false)
const issueTarget = ref<ReviewTarget | null>(null)
const issueTargetLabel = ref('')
const issueReason = ref('')
const issueError = ref('')
const completeOpen = ref(false)
const completeError = ref('')
const searchInput = ref('')
const appliedSearch = ref('')
const assignmentRecoveryPending = ref(false)
let workbenchGeneration = 0

const currentArticle = computed<TaskArticleSnapshot | null>(() => {
  const detail = review.value
  const target = selected.value
  if (!detail || target.kind !== 'article') return null
  return detail.contentVersionSnapshot.articles.find((article) => article.articleId === target.articleId) ?? null
})

const currentItem = computed<ReviewItem | null>(() => {
  if (!review.value) return null
  return buildReviewItemMap(review.value.items).get(reviewTargetKey(selected.value)) ?? null
})

const capabilities = computed(() => review.value
  ? reviewTargetCapabilities(review.value, selected.value)
  : { inScope: false, state: null, canCheck: false, canIssue: false, canCheckAndNext: false })

const anyWriteBusy = computed(() => startBusy.value || checkBusy.value || issueBusy.value || completeBusy.value)
const completeAllowed = computed(() => review.value ? canCompleteReview(review.value) : false)
const completionResumeAllowed = computed(() => review.value
  ? canResumeReviewCompletion(review.value, authState.user?.id)
  : false)
const roundLabel = computed(() => review.value?.roundType === 'REREVIEW' ? '复审' : '初审')
const startLabel = computed(() => task.value?.taskState === 'PENDING_REREVIEW' ? '开始复审' : '开始初审')
const targetLabel = computed(() => selected.value.kind === 'overall' ? '整体信息' : (currentArticle.value?.number ?? '当前法条'))

const previousTarget = computed<ReviewTarget | null>(() => {
  if (!review.value) return null
  const order = buildReviewTargetOrder(review.value)
  const index = order.findIndex((target) => reviewTargetKey(target) === reviewTargetKey(selected.value))
  return index > 0 ? order[index - 1] : null
})

const nextTarget = computed<ReviewTarget | null>(() => review.value
  ? findNextReviewTarget(review.value, selected.value)
  : null)

const issueTargetDisabled = computed(() => {
  if (!review.value || !issueTarget.value) return true
  return !reviewTargetCapabilities(review.value, issueTarget.value).canIssue
})

function reviewErrorMessage(error: unknown, fallback: string): string {
  const failure = parseFailure(error)
  const messages: Record<string, string> = {
    'REVIEW.ALREADY_ASSIGNED': '该审核轮次已由其他管理员领取，正在刷新为只读模式。',
    'REVIEW.NOT_REVIEWER': '你已不是本轮可写审核人，正在刷新审核状态。',
    'REVIEW.INVALID_TASK_STATE': '任务状态已经变化，正在刷新最新审核状态。',
    'REVIEW.ITEM_NOT_IN_SCOPE': '审核范围已经变化，正在刷新本轮审核项。',
    'REVIEW.ALREADY_COMPLETED': '本轮审核已经完成，正在刷新审核结果。',
    'REVIEW.COMPLETION_CONFLICT': '审核状态发生并发变化，正在刷新后重试。',
    'REVIEW.SOURCE_INVALID': '审核来源快照无效，当前无法继续审核。',
    'REVIEW.ISSUE_REASON_INVALID': '问题原因不符合要求，请检查后重试。',
    'REVIEW.INCOMPLETE': failure.userMessage || '仍有审核项未处理，当前不能完成本轮。',
  }
  return (failure.code && messages[failure.code]) || failure.userMessage || safeErrorMessage(error, fallback)
}

function operationCurrent(generation: number, operationTaskId: string): boolean {
  return generation === workbenchGeneration && taskId.value === operationTaskId
}

function applyReview(response: ReviewDetail, resetSelection: boolean): void {
  review.value = response
  notStarted.value = false
  assignmentRecoveryPending.value = false
  if (task.value) task.value = { ...task.value, taskState: response.taskState }
  const targetStillExists = buildReviewTargetOrder(response)
    .some((target) => reviewTargetKey(target) === reviewTargetKey(selected.value))
  if (resetSelection || !targetStillExists) selected.value = selectInitialReviewTarget(response)
}

async function loadWorkbench(resetSelection = true): Promise<void> {
  const generation = ++workbenchGeneration
  const operationTaskId = taskId.value
  loading.value = true
  loadError.value = ''
  try {
    const taskResponse = await getTask(operationTaskId)
    if (!operationCurrent(generation, operationTaskId)) return
    task.value = taskResponse
    try {
      const reviewResponse = await getReview(operationTaskId)
      if (!operationCurrent(generation, operationTaskId)) return
      applyReview(reviewResponse, resetSelection)
    } catch (error: unknown) {
      if (!operationCurrent(generation, operationTaskId)) return
      const failure = parseFailure(error)
      if (reviewFailureDecision(failure.code) === 'not-started'
        && (taskResponse.taskState === 'PENDING_REVIEW' || taskResponse.taskState === 'PENDING_REREVIEW')) {
        review.value = null
        notStarted.value = true
        selected.value = { kind: 'overall' }
      } else {
        throw error
      }
    }
  } catch (error: unknown) {
    if (operationCurrent(generation, operationTaskId)) {
      review.value = null
      notStarted.value = false
      loadError.value = reviewErrorMessage(error, '审核工作台加载失败，请稍后重试')
    }
  } finally {
    if (operationCurrent(generation, operationTaskId)) loading.value = false
  }
}

async function refreshAfterConflict(error: unknown, fallback: string, resetSelection = false): Promise<void> {
  notify(reviewErrorMessage(error, fallback), 'error')
  await loadWorkbench(resetSelection)
}

async function recoverAssignedReview(generation: number, operationTaskId: string): Promise<void> {
  assignmentRecoveryPending.value = true
  loading.value = true
  loadError.value = ''
  try {
    const response = await readAssignedReviewWithRetry(
      () => getReview(operationTaskId),
      (error) => parseFailure(error).code === 'REVIEW.NOT_STARTED',
    )
    if (!operationCurrent(generation, operationTaskId)) return
    applyReview(response, true)
  } catch (error: unknown) {
    if (!operationCurrent(generation, operationTaskId)) return
    review.value = null
    notStarted.value = false
    loadError.value = parseFailure(error).code === 'REVIEW.NOT_STARTED'
      ? '审核状态正在更新，请稍后重试。'
      : reviewErrorMessage(error, '审核状态读取失败，请稍后重试')
  } finally {
    if (operationCurrent(generation, operationTaskId)) loading.value = false
  }
}

async function retryWorkbenchLoad(): Promise<void> {
  if (assignmentRecoveryPending.value) {
    await recoverAssignedReview(workbenchGeneration, taskId.value)
    return
  }
  await loadWorkbench(true)
}

async function startCurrentReview(): Promise<void> {
  if (!task.value || startBusy.value || !notStarted.value) return
  const generation = workbenchGeneration
  const operationTaskId = task.value.taskId
  startBusy.value = true
  try {
    const response = await startReview(operationTaskId)
    if (!operationCurrent(generation, operationTaskId)) return
    applyReview(response, true)
    notify(`${response.roundType === 'REREVIEW' ? '复审' : '初审'}已开始`, 'success')
  } catch (error: unknown) {
    if (!operationCurrent(generation, operationTaskId)) return
    const failure = parseFailure(error)
    const decision = reviewFailureDecision(failure.code)
    if (failure.code === 'REVIEW.ALREADY_ASSIGNED') {
      notify(reviewErrorMessage(error, '审核已由其他管理员领取，正在读取最新状态'), 'info')
      await recoverAssignedReview(generation, operationTaskId)
    } else if (decision === 'reload') await refreshAfterConflict(error, '开始审核失败，请刷新后重试', true)
    else notify(reviewErrorMessage(error, '开始审核失败，请稍后重试'), 'error')
  } finally {
    startBusy.value = false
  }
}

async function markChecked(moveNext: boolean): Promise<void> {
  if (!review.value || checkBusy.value || anyWriteBusy.value || !capabilities.value.canCheck) return
  if (moveNext && !capabilities.value.canCheckAndNext) return
  const generation = workbenchGeneration
  const operationTaskId = review.value.taskId
  const roundId = review.value.reviewRoundId
  const target = { ...selected.value } as ReviewTarget
  checkBusy.value = true
  try {
    const response = await checkReviewItem(operationTaskId, roundId, target)
    if (!operationCurrent(generation, operationTaskId)) return
    applyReview(response, false)
    if (!moveNext) {
      notify(`${targetLabel.value}已标记为无问题`, 'success')
      return
    }
    const next = findNextReviewTarget(response, target)
    if (next) selected.value = next
    else notify(
      response.progress.unreviewed === 0
        ? '已经是最后一项，本轮所有必审项均已处理，可以完成本轮审核。'
        : '当前项已核查，已经是目录最后一项。',
      'success',
    )
  } catch (error: unknown) {
    if (!operationCurrent(generation, operationTaskId)) return
    if (reviewFailureDecision(parseFailure(error).code) === 'reload') {
      await refreshAfterConflict(error, '审核状态保存失败，请刷新后重试')
    } else {
      notify(reviewErrorMessage(error, '审核状态保存失败，请稍后重试'), 'error')
    }
  } finally {
    checkBusy.value = false
  }
}

function moveToNextTarget(): void {
  if (nextTarget.value) {
    selected.value = nextTarget.value
    return
  }
  notify(
    review.value?.progress.unreviewed === 0
      ? '已经是最后一项，本轮所有必审项均已处理，可以完成本轮审核。'
      : '已经是目录最后一项。',
    'info',
  )
}

function openIssueModal(): void {
  if (!review.value || !capabilities.value.canIssue) return
  issueTarget.value = { ...selected.value } as ReviewTarget
  issueTargetLabel.value = targetLabel.value
  issueReason.value = currentItem.value?.issue?.reason ?? ''
  issueError.value = ''
  issueOpen.value = true
}

function closeIssueModal(): void {
  if (issueBusy.value) return
  resetIssueModal()
}

function resetIssueModal(): void {
  issueOpen.value = false
  issueTarget.value = null
  issueTargetLabel.value = ''
  issueReason.value = ''
  issueError.value = ''
}

function updateIssueReason(value: string): void {
  issueReason.value = value
  issueError.value = ''
}

async function confirmIssue(reason: string): Promise<void> {
  if (!review.value || !issueTarget.value || issueBusy.value) return
  const validation = validateIssueReason(reason)
  if (validation) {
    issueError.value = validation
    return
  }
  if (issueTargetDisabled.value) {
    issueError.value = '服务器状态已变化，当前不能修改此审核项。'
    return
  }
  const generation = workbenchGeneration
  const operationTaskId = review.value.taskId
  const roundId = review.value.reviewRoundId
  const target = { ...issueTarget.value } as ReviewTarget
  const wasInScope = buildReviewItemMap(review.value.items).has(reviewTargetKey(target))
  issueBusy.value = true
  issueError.value = ''
  try {
    const response = await issueReviewItem(operationTaskId, roundId, target, normalizeIssueReason(reason))
    if (!operationCurrent(generation, operationTaskId)) return
    applyReview(response, false)
    selected.value = target
    resetIssueModal()
    notify(wasInScope ? '问题原因已保存' : '已将新发现问题加入本轮复审范围', 'success')
  } catch (error: unknown) {
    if (!operationCurrent(generation, operationTaskId)) return
    const failure = parseFailure(error)
    issueError.value = failure.locators.find((locator) => locator.path === 'reason')?.message
      || reviewErrorMessage(error, '问题保存失败，请稍后重试')
    if (reviewFailureDecision(failure.code) === 'reload') await loadWorkbench(false)
  } finally {
    issueBusy.value = false
  }
}

function openCompleteModal(): void {
  if (!review.value || !canCompleteReview(review.value)) return
  completeError.value = ''
  completeOpen.value = true
}

async function confirmComplete(): Promise<void> {
  if (!review.value || completeBusy.value || !canCompleteReview(review.value)) return
  await completeCurrentReview()
}

async function resumeCompletion(): Promise<void> {
  if (!review.value || completeBusy.value || !completionResumeAllowed.value) return
  await completeCurrentReview()
}

async function completeCurrentReview(): Promise<void> {
  if (!review.value || completeBusy.value) return
  const generation = workbenchGeneration
  const operationTaskId = review.value.taskId
  const roundId = review.value.reviewRoundId
  completeBusy.value = true
  completeError.value = ''
  try {
    const response = await completeReviewRound(operationTaskId, roundId)
    if (!operationCurrent(generation, operationTaskId)) return
    applyReview(response, false)
    completeOpen.value = false
    completeError.value = ''
    notify(response.outcome === 'APPROVED' ? '本轮审核已通过' : '本轮审核已完成并部分驳回', 'success')
  } catch (error: unknown) {
    if (!operationCurrent(generation, operationTaskId)) return
    completeError.value = reviewErrorMessage(error, '完成审核失败，请稍后重试')
    if (reviewFailureDecision(parseFailure(error).code) === 'reload') {
      await loadWorkbench(false)
      if (review.value?.completionStartedAt || review.value?.completedAt) completeOpen.value = false
    }
  } finally {
    completeBusy.value = false
  }
}

function structurePath(articleId: string): string {
  if (!review.value) return ''
  const nodes = review.value.structureSnapshot
  const byId = new Map(nodes.map((node) => [node.nodeId, node]))
  let node = nodes.find((candidate) => candidate.articleIds.includes(articleId)) ?? null
  const titles: string[] = []
  while (node) {
    if (node.title.trim()) titles.unshift(node.title.trim())
    node = node.parentNodeId ? (byId.get(node.parentNodeId) ?? null) : null
  }
  return titles.join(' / ')
}

function runSearch(): void {
  appliedSearch.value = searchInput.value.trim()
}

function clearSearch(): void {
  searchInput.value = ''
  appliedSearch.value = ''
}

watch(taskId, () => {
  assignmentRecoveryPending.value = false
  issueOpen.value = false
  completeOpen.value = false
  clearSearch()
  void loadWorkbench(true)
})
watch(searchInput, (value) => { if (!value.trim()) appliedSearch.value = '' })
onMounted(() => void loadWorkbench(true))
</script>

<template>
  <div class="review-page">
    <RouterLink class="review-back" :to="{ name: 'admin-task-detail', params: { taskId } }">← 返回任务详情</RouterLink>
    <section v-if="loading" class="panel review-state"><span class="spinner" />正在加载审核工作台…</section>
    <section v-else-if="loadError" class="panel review-state review-state--error">
      <p>{{ loadError }}</p><button class="button" type="button" @click="retryWorkbenchLoad">重新加载</button>
    </section>
    <template v-else-if="task">
      <header class="review-page-heading">
        <div><h1>{{ task.taskName }}</h1><p>{{ task.lawBaseInfoSnapshot.name }} · {{ task.contentVersionSnapshot.articles.length }} 条法条</p></div>
        <TaskStatusBadge :state="task.taskState" />
      </header>

      <section v-if="notStarted" class="panel review-pending">
        <div><h2>当前{{ task.taskState === 'PENDING_REREVIEW' ? '复审' : '初审' }}尚未开始</h2><p>只有明确点击开始后，当前管理员才会领取本轮审核。直接进入页面不会自动领取。</p></div>
        <button class="button button--primary" type="button" :disabled="startBusy" @click="startCurrentReview">
          <span v-if="startBusy" class="spinner" />{{ startBusy ? '开始中…' : startLabel }}
        </button>
      </section>

      <template v-else-if="review">
        <p v-if="review.completedAt" class="review-result-banner" :class="review.outcome === 'APPROVED' ? 'approved' : 'rejected'">
          本轮{{ roundLabel }}已完成：<strong>{{ review.outcome === 'APPROVED' ? '审核通过' : '部分驳回' }}</strong>。当前页面为只读结果。
        </p>
        <div v-else-if="review.completionStartedAt" class="review-readonly-banner review-completion-banner">
          <div>
            <strong>本轮审核已进入完成流程，审核结论当前不可再修改。</strong>
            <p v-if="completionResumeAllowed">如果上次完成请求中断，可以继续执行服务器已有的完成意图。</p>
            <p v-else>本轮原审核员可继续完成；其他管理员当前仅可查看。</p>
            <p v-if="completeError" class="field-error">{{ completeError }}</p>
          </div>
          <button
            v-if="completionResumeAllowed"
            class="button button--primary"
            type="button"
            :disabled="anyWriteBusy"
            @click="resumeCompletion"
          ><span v-if="completeBusy" class="spinner" />{{ completeBusy ? '继续完成中…' : '继续完成' }}</button>
        </div>
        <p v-else-if="!review.writable" class="review-readonly-banner">该审核轮次已由其他管理员领取，当前仅可查看。</p>
        <p v-else-if="review.roundType === 'REREVIEW'" class="review-scope-note">复审必审范围以服务器 items 为准；你仍可浏览整部法律并新增遗漏问题。</p>

        <div class="review-workbench">
          <ReviewStructureTree
            :review="review"
            :selected="selected"
            :search-input="searchInput"
            :applied-search="appliedSearch"
            :disabled="anyWriteBusy"
            @target="selected = $event"
            @update:search-input="searchInput = $event"
            @search="runSearch"
            @clear-search="clearSearch"
          />
          <main class="review-editor panel">
            <ReviewContentPanel
              :review="review"
              :target="selected"
              :article="currentArticle"
              :structure-path="selected.kind === 'article' ? structurePath(selected.articleId) : ''"
            />
            <footer class="review-actions">
              <button v-if="capabilities.state === 'UNREVIEWED'" class="button" type="button" :disabled="anyWriteBusy" @click="moveToNextTarget">暂不处理</button>
              <button v-if="capabilities.canIssue" class="button button--danger" type="button" :disabled="anyWriteBusy" @click="openIssueModal">
                {{ capabilities.inScope ? (capabilities.state === 'NEEDS_CHANGE' ? '修改问题' : '标记问题') : '发现新问题' }}
              </button>
              <span class="review-actions-spacer" />
              <button class="button" type="button" :disabled="!previousTarget || anyWriteBusy" @click="selected = previousTarget!">上一项</button>
              <button v-if="capabilities.canCheck" class="button" type="button" :disabled="anyWriteBusy" @click="markChecked(false)">
                <span v-if="checkBusy" class="spinner" />标记无问题
              </button>
              <button v-if="capabilities.canCheckAndNext" class="button button--primary" type="button" :disabled="anyWriteBusy" @click="markChecked(true)">
                <span v-if="checkBusy" class="spinner" />下一项并核查
              </button>
              <button v-else class="button" type="button" :disabled="anyWriteBusy" @click="moveToNextTarget">下一项</button>
              <div v-if="review.writable && !review.completedAt" class="review-complete-action">
                <button class="button" type="button" :disabled="anyWriteBusy || !completeAllowed" @click="openCompleteModal">完成本轮审核</button>
                <small v-if="review.progress.unreviewed > 0">尚有 {{ review.progress.unreviewed }} 项未审核</small>
              </div>
            </footer>
          </main>
        </div>

        <ReviewIssueModal
          :open="issueOpen"
          :target-label="issueTargetLabel"
          :reason="issueReason"
          :busy="issueBusy"
          :server-error="issueError"
          :disabled="issueTargetDisabled"
          @close="closeIssueModal"
          @confirm="confirmIssue"
          @update:reason="updateIssueReason"
        />
        <ReviewCompleteModal
          :open="completeOpen"
          :review="review"
          :busy="completeBusy"
          :error="completeError"
          :disabled="!completeAllowed"
          @close="completeOpen = false; completeError = ''"
          @confirm="confirmComplete"
        />
      </template>
    </template>
  </div>
</template>

<style src="./review.css"></style>
