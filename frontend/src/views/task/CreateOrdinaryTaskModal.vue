<script setup lang="ts">
import { reactive, ref, watch } from 'vue'

import { listLaws, getLaw } from '../../api/laws'
import { createOrdinaryTask, listTasks } from '../../api/tasks'
import { listUsers } from '../../api/users'
import type { PageResponse, User } from '../../api/types'
import type { LawListItem } from '../../types/law'
import type { TaskDetail } from '../../types/task'
import { isUnfinishedTaskState } from '../../types/task'
import { fieldErrors, parseFailure, safeErrorMessage } from '../../utils/errors'
import AppModal from '../../components/AppModal.vue'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: []; created: [task: TaskDetail] }>()

const EMPTY_LAW_PAGE: PageResponse<LawListItem> = {
  items: [], page: 0, size: 10, totalElements: 0, totalPages: 0,
}

const form = reactive({ lawId: '', annotatorId: '', taskName: '', remark: '' })
const errors = reactive({ lawId: '', annotatorId: '', taskName: '', remark: '' })
const serverError = ref('')
const submitting = ref(false)

const lawSearchInput = ref('')
const appliedLawSearch = ref('')
const lawPage = ref(0)
const rawLawPage = ref<PageResponse<LawListItem>>(EMPTY_LAW_PAGE)
const eligibleLaws = ref<LawListItem[]>([])
const lawsLoading = ref(false)
const lawsError = ref('')
let lawRequestSequence = 0
const MAX_PROBE_CONCURRENCY = 4
let activeProbeRequests = 0
const probeWaiters: Array<() => void> = []

const annotators = ref<User[]>([])
const annotatorsLoading = ref(false)
const annotatorsError = ref('')
let annotatorRequestSequence = 0

function resetForm(): void {
  Object.assign(form, { lawId: '', annotatorId: '', taskName: '', remark: '' })
  Object.assign(errors, { lawId: '', annotatorId: '', taskName: '', remark: '' })
  serverError.value = ''
  lawSearchInput.value = ''
  appliedLawSearch.value = ''
  lawPage.value = 0
  rawLawPage.value = EMPTY_LAW_PAGE
  eligibleLaws.value = []
}

async function withProbeLimit<T>(
  request: () => Promise<T>,
  shouldRun: () => boolean,
): Promise<T | null> {
  if (activeProbeRequests >= MAX_PROBE_CONCURRENCY) {
    await new Promise<void>((resolve) => probeWaiters.push(resolve))
  } else {
    activeProbeRequests += 1
  }
  try {
    if (!shouldRun()) return null
    return await request()
  } finally {
    const next = probeWaiters.shift()
    if (next) {
      next()
    } else {
      activeProbeRequests -= 1
    }
  }
}

async function isEligibleByPublicData(law: LawListItem, sequence: number): Promise<boolean> {
  const isCurrent = () => sequence === lawRequestSequence && props.open
  const detail = await withProbeLimit(() => getLaw(law.id), isCurrent)
  if (!detail) return false
  const taskPage = await withProbeLimit(
    () => listTasks({ lawId: law.id, page: 0, size: 1 }),
    isCurrent,
  )
  if (!taskPage) return false
  const latestTask = taskPage.items[0]
  return Boolean(detail.currentContentVersionId)
    && detail.articles.length > 0
    && !detail.pendingRevision
    && (!latestTask || !isUnfinishedTaskState(latestTask.taskState))
}

async function loadEligibleLaws(preserveSelection = false): Promise<void> {
  const sequence = ++lawRequestSequence
  lawsLoading.value = true
  lawsError.value = ''
  try {
    const page = await listLaws(appliedLawSearch.value, lawPage.value)
    const potential = page.items.filter((law) => law.articleCount > 0)
    const flags = await Promise.all(potential.map((law) => isEligibleByPublicData(law, sequence)))
    if (sequence !== lawRequestSequence || !props.open) return
    rawLawPage.value = page
    eligibleLaws.value = potential.filter((_, index) => flags[index])
    if (!preserveSelection || !eligibleLaws.value.some((law) => law.id === form.lawId)) {
      form.lawId = ''
    }
  } catch (error: unknown) {
    if (sequence !== lawRequestSequence || !props.open) return
    rawLawPage.value = EMPTY_LAW_PAGE
    eligibleLaws.value = []
    form.lawId = ''
    lawsError.value = safeErrorMessage(error, '候选法律加载失败，请稍后重试')
  } finally {
    if (sequence === lawRequestSequence) lawsLoading.value = false
  }
}

async function loadAnnotators(preserveSelection = false): Promise<void> {
  const sequence = ++annotatorRequestSequence
  annotatorsLoading.value = true
  annotatorsError.value = ''
  try {
    const collected: User[] = []
    let page = 0
    let totalPages = 1
    while (page < totalPages) {
      const response = await listUsers({ role: 'ANNOTATOR', enabled: true, page, size: 100 })
      collected.push(...response.items)
      totalPages = response.totalPages
      page += 1
    }
    if (sequence !== annotatorRequestSequence || !props.open) return
    annotators.value = collected
    if (!preserveSelection || !annotators.value.some((user) => user.id === form.annotatorId)) {
      form.annotatorId = ''
    }
  } catch (error: unknown) {
    if (sequence !== annotatorRequestSequence || !props.open) return
    annotators.value = []
    form.annotatorId = ''
    annotatorsError.value = safeErrorMessage(error, '候选标注员加载失败，请稍后重试')
  } finally {
    if (sequence === annotatorRequestSequence) annotatorsLoading.value = false
  }
}

async function applyLawSearch(): Promise<void> {
  appliedLawSearch.value = lawSearchInput.value.trim()
  lawPage.value = 0
  form.lawId = ''
  await loadEligibleLaws()
}

async function goToLawPage(page: number): Promise<void> {
  if (page < 0 || page >= rawLawPage.value.totalPages || page === lawPage.value) return
  lawPage.value = page
  form.lawId = ''
  await loadEligibleLaws()
}

function validateText(value: string, maxLength: number, emptyAllowed: boolean): string | null {
  const trimmed = value.trim()
  if (!trimmed) return emptyAllowed ? null : '不能为空'
  if ([...trimmed].length > maxLength) return `不能超过${maxLength}个字符`
  if ([...trimmed].some((character) => /[\u0000-\u001f\u007f]/.test(character))) {
    return '不能包含控制字符'
  }
  return null
}

async function submit(): Promise<void> {
  errors.lawId = form.lawId ? '' : '请选择法律'
  errors.annotatorId = form.annotatorId ? '' : '请选择标注员'
  errors.taskName = validateText(form.taskName, 100, true) ?? ''
  errors.remark = validateText(form.remark, 500, true) ?? ''
  serverError.value = ''
  if (Object.values(errors).some(Boolean) || submitting.value) return

  submitting.value = true
  try {
    const taskName = form.taskName.trim()
    const remark = form.remark.trim()
    const created = await createOrdinaryTask({
      lawId: form.lawId,
      annotatorId: form.annotatorId,
      ...(taskName ? { taskName } : {}),
      ...(remark ? { remark } : {}),
    })
    emit('created', created)
  } catch (error: unknown) {
    const fields = fieldErrors(error)
    errors.lawId = fields.lawId ?? ''
    errors.annotatorId = fields.annotatorId ?? ''
    errors.taskName = fields.taskName ?? ''
    errors.remark = fields.remark ?? ''
    serverError.value = Object.values(errors).some(Boolean)
      ? ''
      : safeErrorMessage(error, '创建任务失败，请稍后重试')
    if (parseFailure(error).status === 409) {
      await Promise.all([loadEligibleLaws(true), loadAnnotators(true)])
    }
  } finally {
    submitting.value = false
  }
}

watch(
  () => props.open,
  (open) => {
    if (!open) {
      lawRequestSequence += 1
      annotatorRequestSequence += 1
      return
    }
    resetForm()
    void Promise.all([loadEligibleLaws(), loadAnnotators()])
  },
)
</script>

<template>
  <AppModal :open="open" title="创建普通任务" width="680px" :busy="submitting" @close="emit('close')">
    <form id="create-task-form" class="form-grid" @submit.prevent="submit">
      <div class="readonly-summary"><span>任务类型</span><strong>普通标注</strong><small>任务自动包含所选法律的全部有效法条</small></div>

      <div class="form-field">
        <label for="task-name">任务名称</label>
        <input id="task-name" v-model="form.taskName" class="input" maxlength="100" placeholder="留空则由系统自动生成" :disabled="submitting" />
        <p v-if="errors.taskName" class="field-error">{{ errors.taskName }}</p>
      </div>

      <fieldset class="candidate-fieldset">
        <legend>法律文件 <span class="required">*</span></legend>
        <div class="candidate-search">
          <input v-model="lawSearchInput" class="input" maxlength="100" placeholder="按法律名称搜索" :disabled="lawsLoading || submitting" aria-label="搜索候选法律" @keydown.enter.prevent="applyLawSearch" />
          <button class="button" type="button" :disabled="lawsLoading || submitting" @click="applyLawSearch">查询</button>
        </div>
        <div v-if="lawsLoading" class="candidate-state"><span class="spinner" />正在核验当前页候选法律…</div>
        <div v-else-if="lawsError" class="candidate-state candidate-state--error"><span>{{ lawsError }}</span><button class="button" type="button" @click="loadEligibleLaws()">重试</button></div>
        <div v-else-if="eligibleLaws.length === 0" class="candidate-state">当前页暂无可选法律</div>
        <div v-else class="candidate-list">
          <label v-for="law in eligibleLaws" :key="law.id" class="candidate-option" :class="{ selected: form.lawId === law.id }">
            <input v-model="form.lawId" type="radio" name="lawId" :value="law.id" :disabled="submitting" />
            <span><strong>{{ law.name }}</strong><small>{{ law.issuingAuthority }} · {{ law.articleCount }} 条法条</small></span>
          </label>
        </div>
        <footer v-if="!lawsLoading && !lawsError && rawLawPage.totalPages > 1" class="candidate-pagination">
          <button class="button" type="button" :disabled="lawPage === 0 || submitting" @click="goToLawPage(lawPage - 1)">上一页</button>
          <span>原始法律第 {{ lawPage + 1 }} / {{ rawLawPage.totalPages }} 页</span>
          <button class="button" type="button" :disabled="lawPage + 1 >= rawLawPage.totalPages || submitting" @click="goToLawPage(lawPage + 1)">下一页</button>
        </footer>
        <p class="form-note">候选列表仅做公开数据可判定的预过滤，提交时由后端最终校验资格。</p>
        <p v-if="errors.lawId" class="field-error">{{ errors.lawId }}</p>
      </fieldset>

      <div class="form-field">
        <label for="task-annotator">标注员 <span class="required">*</span></label>
        <select id="task-annotator" v-model="form.annotatorId" class="select" :disabled="annotatorsLoading || submitting">
          <option value="">{{ annotatorsLoading ? '正在加载标注员…' : '请选择启用标注员' }}</option>
          <option v-for="annotator in annotators" :key="annotator.id" :value="annotator.id">{{ annotator.name }}（{{ annotator.loginAccount }}）</option>
        </select>
        <p v-if="annotatorsError" class="field-error">{{ annotatorsError }} <button class="inline-retry" type="button" @click="loadAnnotators()">重试</button></p>
        <p v-if="errors.annotatorId" class="field-error">{{ errors.annotatorId }}</p>
      </div>

      <div class="form-field">
        <label for="task-remark">任务备注</label>
        <textarea id="task-remark" v-model="form.remark" class="task-textarea" rows="4" maxlength="500" placeholder="标注员可见，可填写处理说明" :disabled="submitting" />
        <div class="field-row"><p v-if="errors.remark" class="field-error">{{ errors.remark }}</p><span>{{ [...form.remark.trim()].length }} / 500</span></div>
      </div>
      <p v-if="serverError" class="field-error">{{ serverError }}</p>
    </form>
    <template #footer>
      <button class="button" type="button" :disabled="submitting" @click="emit('close')">取消</button>
      <button class="button button--primary" type="submit" form="create-task-form" :disabled="submitting || lawsLoading || annotatorsLoading">
        <span v-if="submitting" class="spinner" />{{ submitting ? '创建中…' : '确认创建' }}
      </button>
    </template>
  </AppModal>
</template>

<style scoped>
.readonly-summary { display: grid; gap: 5px; border-radius: 6px; background: #f5f7fa; padding: 14px; }
.readonly-summary span, .readonly-summary small { color: #7c8797; font-size: 12px; }
.candidate-fieldset { min-width: 0; margin: 0; border: 1px solid #dfe5ec; border-radius: 7px; padding: 14px; }
.candidate-fieldset legend { color: #344054; padding: 0 5px; font-size: 14px; font-weight: 600; }
.candidate-search { display: flex; gap: 8px; margin-bottom: 12px; }.candidate-search .input { flex: 1; }
.candidate-state { display: flex; min-height: 88px; align-items: center; justify-content: center; gap: 10px; color: #788395; font-size: 13px; }
.candidate-state--error { color: #a63c3c; }
.candidate-list { display: grid; max-height: 210px; overflow-y: auto; border: 1px solid #e4e9ef; border-radius: 6px; }
.candidate-option { display: flex; align-items: center; gap: 10px; border-bottom: 1px solid #edf0f4; padding: 11px 12px; cursor: pointer; }
.candidate-option:last-child { border-bottom: 0; }.candidate-option:hover, .candidate-option.selected { background: #f0f6ff; }
.candidate-option span { display: grid; gap: 3px; }.candidate-option strong { font-size: 13px; }.candidate-option small { color: #788395; font-size: 12px; }
.candidate-pagination { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding-top: 12px; color: #6d7787; font-size: 12px; }
.candidate-pagination .button { min-height: 30px; padding: 0 10px; }
.form-note { margin-top: 11px; }
.task-textarea { width: 100%; resize: vertical; border: 1px solid #cfd6df; border-radius: 6px; outline: none; padding: 10px 12px; line-height: 1.6; }
.task-textarea:focus { border-color: #3b78d2; box-shadow: 0 0 0 3px rgb(59 120 210 / 12%); }.task-textarea:disabled { background: #f4f6f8; }
.field-row { display: flex; min-height: 18px; justify-content: space-between; color: #8a94a4; font-size: 12px; }.field-row .field-error { font-size: 13px; }
.inline-retry { border: 0; background: transparent; color: #2868c7; padding: 0 4px; cursor: pointer; }
@media (max-width: 560px) { .candidate-search { flex-direction: column; }.candidate-pagination { align-items: stretch; flex-direction: column; text-align: center; } }
</style>
