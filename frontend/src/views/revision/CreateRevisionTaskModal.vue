<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'

import AppModal from '../../components/AppModal.vue'
import { getLaw, listLaws } from '../../api/laws'
import { createRevisionTask } from '../../api/tasks'
import type { PageResponse, User } from '../../api/types'
import { listUsers } from '../../api/users'
import type { LawDetail, LawListItem } from '../../types/law'
import type { TaskDetail } from '../../types/task'
import { fieldErrors, parseFailure, safeErrorMessage } from '../../utils/errors'
import {
  buildRevisionTaskPayload,
  failedRevisionAnnotatorState,
  loadedRevisionAnnotatorState,
  orderedRevisionArticles,
  resetRevisionAnnotatorState,
  revisionCandidateKind,
  type RevisionAnnotatorState,
  type RevisionCandidateKind,
  validateRevisionScope,
} from './revisionTaskState'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: []; created: [task: TaskDetail] }>()

const PAGE_SIZE = 10
const EMPTY_PAGE: PageResponse<LawListItem> = {
  items: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0,
}
const form = reactive({
  candidateKind: 'ANNOTATION_ONLY' as RevisionCandidateKind,
  lawId: '', annotatorId: '', taskName: '', remark: '', overall: false, articleIds: [] as string[],
})
const errors = reactive({ lawId: '', annotatorId: '', taskName: '', remark: '', scope: '' })
const serverError = ref('')
const submitting = ref(false)
const lawSearchInput = ref('')
const appliedLawSearch = ref('')
const lawPage = ref(0)
const lawResult = ref<PageResponse<LawListItem>>(EMPTY_PAGE)
const lawsLoading = ref(false)
const lawsError = ref('')
const selectedLaw = ref<LawDetail | null>(null)
const detailLoading = ref(false)
const detailError = ref('')
const annotators = ref<User[]>([])
const annotatorsLoading = ref(false)
const annotatorsError = ref('')
let lawSequence = 0
let detailSequence = 0
let annotatorSequence = 0

const displayStatus = computed(() => form.candidateKind === 'ANNOTATION_ONLY'
  ? 'COMPLETED' as const
  : 'PENDING_REVISION' as const)
const orderedArticles = computed(() => selectedLaw.value
  ? orderedRevisionArticles(selectedLaw.value)
  : [])

function applyAnnotatorState(state: RevisionAnnotatorState): void {
  annotators.value = state.annotators
  form.annotatorId = state.annotatorId
  annotatorsError.value = state.annotatorsError
}

function reset(): void {
  Object.assign(form, {
    candidateKind: 'ANNOTATION_ONLY', lawId: '', annotatorId: '',
    taskName: '', remark: '', overall: false, articleIds: [],
  })
  Object.assign(errors, { lawId: '', annotatorId: '', taskName: '', remark: '', scope: '' })
  serverError.value = ''
  lawSearchInput.value = ''
  appliedLawSearch.value = ''
  lawPage.value = 0
  lawResult.value = EMPTY_PAGE
  selectedLaw.value = null
  lawsLoading.value = false
  detailLoading.value = false
  annotatorsLoading.value = false
  applyAnnotatorState(resetRevisionAnnotatorState())
  detailError.value = ''
}

function validateText(value: string, maximum: number): string | null {
  const trimmed = value.trim()
  if ([...trimmed].length > maximum) return `不能超过${maximum}个字符`
  if ([...trimmed].some((character) => /[\u0000-\u001f\u007f]/.test(character))) {
    return '不能包含控制字符'
  }
  return null
}

async function loadLaws(preserveSelection = false): Promise<void> {
  const sequence = ++lawSequence
  lawsLoading.value = true
  lawsError.value = ''
  try {
    const page = await listLaws({
      name: appliedLawSearch.value || undefined,
      displayStatus: displayStatus.value,
      page: lawPage.value,
      size: PAGE_SIZE,
    })
    if (sequence !== lawSequence || !props.open) return
    lawResult.value = page
    if (!preserveSelection) {
      form.lawId = ''
      form.articleIds = []
      selectedLaw.value = null
    }
  } catch (error: unknown) {
    if (sequence !== lawSequence || !props.open) return
    lawResult.value = EMPTY_PAGE
    lawsError.value = safeErrorMessage(error, '候选法律加载失败，请稍后重试')
  } finally {
    if (sequence === lawSequence) lawsLoading.value = false
  }
}

async function loadSelectedLawDetail(preserveSelection = false): Promise<void> {
  const lawId = form.lawId
  const sequence = ++detailSequence
  selectedLaw.value = null
  detailError.value = ''
  if (!lawId) return
  detailLoading.value = true
  try {
    const detail = await getLaw(lawId)
    if (sequence !== detailSequence || lawId !== form.lawId || !props.open) return
    const statusMatches = revisionCandidateKind(detail.displayStatus) === form.candidateKind
    const annotationOnlyMatches = form.candidateKind !== 'ANNOTATION_ONLY' || !detail.pendingRevision
    if (!statusMatches || !annotationOnlyMatches) {
      form.lawId = ''
      form.articleIds = []
      detailError.value = '该法律已不再符合当前修订任务条件，请重新选择'
      return
    }
    selectedLaw.value = detail
    const currentIds = new Set(detail.articles.map((article) => article.articleId))
    form.articleIds = preserveSelection
      ? form.articleIds.filter((articleId) => currentIds.has(articleId))
      : []
  } catch (error: unknown) {
    if (sequence !== detailSequence || lawId !== form.lawId || !props.open) return
    if (parseFailure(error).status === 404) {
      form.lawId = ''
      form.articleIds = []
      selectedLaw.value = null
    }
    detailError.value = safeErrorMessage(error, '法律详情加载失败，请重新选择或重试')
  } finally {
    if (sequence === detailSequence) detailLoading.value = false
  }
}

async function selectLaw(law: LawListItem): Promise<void> {
  if (revisionCandidateKind(law.displayStatus) !== form.candidateKind) {
    errors.lawId = '该法律的最新状态与当前修订来源不匹配，请刷新候选列表'
    return
  }
  form.lawId = law.id
  form.articleIds = []
  errors.lawId = ''
  errors.scope = ''
  if (form.candidateKind === 'ANNOTATION_ONLY') await loadSelectedLawDetail()
}

async function loadAnnotators(preserveSelection = false): Promise<void> {
  const sequence = ++annotatorSequence
  annotatorsLoading.value = true
  annotators.value = []
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
    if (sequence !== annotatorSequence || !props.open) return
    applyAnnotatorState(loadedRevisionAnnotatorState(
      collected,
      form.annotatorId,
      preserveSelection,
    ))
  } catch (error: unknown) {
    if (sequence !== annotatorSequence || !props.open) return
    applyAnnotatorState(failedRevisionAnnotatorState(
      safeErrorMessage(error, '候选标注员加载失败，请稍后重试'),
    ))
  } finally {
    if (sequence === annotatorSequence) annotatorsLoading.value = false
  }
}

async function changeCandidateKind(kind: RevisionCandidateKind): Promise<void> {
  if (form.candidateKind === kind) return
  form.candidateKind = kind
  form.lawId = ''
  form.articleIds = []
  form.overall = false
  detailSequence += 1
  detailLoading.value = false
  selectedLaw.value = null
  detailError.value = ''
  lawPage.value = 0
  errors.lawId = ''
  errors.scope = ''
  await loadLaws()
}

async function applyLawSearch(): Promise<void> {
  appliedLawSearch.value = lawSearchInput.value.trim()
  lawPage.value = 0
  form.lawId = ''
  form.articleIds = []
  selectedLaw.value = null
  await loadLaws()
}

async function goToLawPage(page: number): Promise<void> {
  if (page < 0 || page >= lawResult.value.totalPages || page === lawPage.value) return
  lawPage.value = page
  form.lawId = ''
  form.articleIds = []
  selectedLaw.value = null
  await loadLaws()
}

async function refreshChangedEligibility(): Promise<void> {
  await Promise.all([loadLaws(true), loadAnnotators(true)])
  if (form.lawId) {
    await loadSelectedLawDetail(true)
  }
}

async function submit(): Promise<void> {
  errors.lawId = form.lawId ? '' : '请选择法律'
  errors.annotatorId = form.annotatorId ? '' : '请选择标注员'
  errors.taskName = validateText(form.taskName, 100) ?? ''
  errors.remark = validateText(form.remark, 500) ?? ''
  errors.scope = validateRevisionScope(form.candidateKind, form.overall, form.articleIds) ?? ''
  serverError.value = ''
  if (form.candidateKind === 'ANNOTATION_ONLY' && !selectedLaw.value && form.lawId) {
    errors.lawId = detailError.value || '请等待法律详情加载完成'
  }
  if (Object.values(errors).some(Boolean) || submitting.value) return

  submitting.value = true
  try {
    const created = await createRevisionTask(buildRevisionTaskPayload(form))
    emit('created', created)
  } catch (error: unknown) {
    const fields = fieldErrors(error)
    errors.lawId = fields.lawId ?? ''
    errors.annotatorId = fields.annotatorId ?? ''
    errors.taskName = fields.taskName ?? ''
    errors.remark = fields.remark ?? ''
    errors.scope = fields.articleIds ?? fields.overall ?? ''
    serverError.value = Object.values(errors).some(Boolean)
      ? ''
      : safeErrorMessage(error, '创建修订任务失败，请稍后重试')
    const status = parseFailure(error).status
    if (status === 404 || status === 409 || status === 422) await refreshChangedEligibility()
  } finally {
    submitting.value = false
  }
}

watch(
  () => props.open,
  (open) => {
    if (!open) {
      lawSequence += 1
      detailSequence += 1
      annotatorSequence += 1
      return
    }
    reset()
    void Promise.all([loadLaws(), loadAnnotators()])
  },
)
</script>

<template>
  <AppModal :open="open" title="创建修订任务" width="760px" :busy="submitting" @close="emit('close')">
    <form id="create-revision-task-form" class="form-grid task-create-modal" @submit.prevent="submit">
      <fieldset class="candidate-fieldset">
        <legend>修订来源 <span class="required">*</span></legend>
        <div class="revision-kind-options">
          <label class="revision-kind-option" :class="{ selected: form.candidateKind === 'ANNOTATION_ONLY' }">
            <span><input type="radio" name="candidateKind" value="ANNOTATION_ONLY" :checked="form.candidateKind === 'ANNOTATION_ONLY'" :disabled="submitting" @change="changeCandidateKind('ANNOTATION_ONLY')" /> 主动纠错 / 标注修正</span>
            <small>从已有正式标注中选择整体信息和真实法条范围。</small>
          </label>
          <label class="revision-kind-option" :class="{ selected: form.candidateKind === 'CONTENT_CHANGE' }">
            <span><input type="radio" name="candidateKind" value="CONTENT_CHANGE" :checked="form.candidateKind === 'CONTENT_CHANGE'" :disabled="submitting" @change="changeCandidateKind('CONTENT_CHANGE')" /> 正文变化待修订</span>
            <small>变化法条由服务器根据 pendingChangeSet 自动确定。</small>
          </label>
        </div>
      </fieldset>

      <div class="form-field">
        <label for="revision-task-name">任务名称</label>
        <input id="revision-task-name" v-model="form.taskName" class="input" maxlength="100" placeholder="留空则由系统自动生成" :disabled="submitting" />
        <p v-if="errors.taskName" class="field-error">{{ errors.taskName }}</p>
      </div>

      <fieldset class="candidate-fieldset">
        <legend>法律文件 <span class="required">*</span></legend>
        <div class="candidate-search">
          <input v-model="lawSearchInput" class="input" maxlength="100" placeholder="按法律名称搜索" :disabled="lawsLoading || submitting" aria-label="搜索修订候选法律" @keydown.enter.prevent="applyLawSearch" />
          <button class="button" type="button" :disabled="lawsLoading || submitting" @click="applyLawSearch">查询</button>
        </div>
        <div v-if="lawsLoading" class="candidate-state"><span class="spinner" />正在加载候选法律…</div>
        <div v-else-if="lawsError" class="candidate-state candidate-state--error"><span>{{ lawsError }}</span><button class="button" type="button" @click="loadLaws()">重试</button></div>
        <div v-else-if="lawResult.items.length === 0" class="candidate-state">暂无符合条件的法律</div>
        <div v-else class="candidate-list">
          <label v-for="law in lawResult.items" :key="law.id" class="candidate-option" :class="{ selected: form.lawId === law.id }">
            <input type="radio" name="revisionLawId" :value="law.id" :checked="form.lawId === law.id" :disabled="submitting" @change="selectLaw(law)" />
            <span><strong>{{ law.name }}</strong><small>{{ law.issuingAuthority }} · {{ law.articleCount }} 条法条</small></span>
          </label>
        </div>
        <footer v-if="!lawsLoading && !lawsError && lawResult.totalPages > 1" class="candidate-pagination">
          <button class="button" type="button" :disabled="lawPage === 0 || submitting" @click="goToLawPage(lawPage - 1)">上一页</button>
          <span>第 {{ lawPage + 1 }} / {{ lawResult.totalPages }} 页</span>
          <button class="button" type="button" :disabled="lawPage + 1 >= lawResult.totalPages || submitting" @click="goToLawPage(lawPage + 1)">下一页</button>
        </footer>
        <p class="form-note">候选仅按公开状态预筛，创建资格和修订类型由服务器最终校验。</p>
        <p v-if="errors.lawId" class="field-error">{{ errors.lawId }}</p>
      </fieldset>

      <fieldset class="candidate-fieldset">
        <legend>修订范围 <span class="required">*</span></legend>
        <label class="revision-scope-option"><input v-model="form.overall" type="checkbox" :disabled="submitting" /> 整体信息</label>
        <template v-if="form.candidateKind === 'ANNOTATION_ONLY'">
          <div v-if="detailLoading" class="candidate-state"><span class="spinner" />正在读取最新法律详情…</div>
          <div v-else-if="detailError" class="candidate-state candidate-state--error"><span>{{ detailError }}</span><button v-if="form.lawId" class="button" type="button" @click="loadSelectedLawDetail(true)">重试</button></div>
          <div v-else-if="selectedLaw" class="revision-scope-list">
            <label v-for="article in orderedArticles" :key="article.articleId" class="revision-scope-option">
              <input v-model="form.articleIds" type="checkbox" :value="article.articleId" :disabled="submitting" />
              <span><strong>{{ article.number }}</strong></span>
            </label>
          </div>
          <p v-else class="form-note">请先选择法律，再从服务器最新 LawDetail 中选择真实法条。</p>
        </template>
        <p v-else class="revision-contract-note">正文变化法条和 mandatory 范围由服务器计算；本次请求固定发送 <code>articleIds: []</code>，管理员不能在浏览器端取消 mandatory 法条。</p>
        <p v-if="errors.scope" class="field-error">{{ errors.scope }}</p>
      </fieldset>

      <div class="form-field">
        <label for="revision-annotator">标注员 <span class="required">*</span></label>
        <select id="revision-annotator" v-model="form.annotatorId" class="select" :disabled="annotatorsLoading || submitting">
          <option value="">{{ annotatorsLoading ? '正在加载标注员…' : '请选择启用标注员' }}</option>
          <option v-for="annotator in annotators" :key="annotator.id" :value="annotator.id">{{ annotator.name }}（{{ annotator.loginAccount }}）</option>
        </select>
        <p v-if="annotatorsError" class="field-error">{{ annotatorsError }} <button class="inline-retry" type="button" @click="loadAnnotators(true)">重试</button></p>
        <p v-if="errors.annotatorId" class="field-error">{{ errors.annotatorId }}</p>
      </div>

      <div class="form-field">
        <label for="revision-remark">任务备注</label>
        <textarea id="revision-remark" v-model="form.remark" class="task-textarea" rows="4" maxlength="500" placeholder="标注员可见，可填写修订说明" :disabled="submitting" />
        <div class="field-row"><p v-if="errors.remark" class="field-error">{{ errors.remark }}</p><span>{{ [...form.remark.trim()].length }} / 500</span></div>
      </div>
      <p v-if="serverError" class="field-error">{{ serverError }}</p>
    </form>
    <template #footer>
      <button class="button" type="button" :disabled="submitting" @click="emit('close')">取消</button>
      <button class="button button--primary" type="submit" form="create-revision-task-form" :disabled="submitting || lawsLoading || detailLoading || annotatorsLoading">
        <span v-if="submitting" class="spinner" />{{ submitting ? '创建中…' : '确认创建' }}
      </button>
    </template>
  </AppModal>
</template>

<style src="../task/task.css"></style>
