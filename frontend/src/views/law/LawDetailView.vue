<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  addLawArticle, apiErrorMessage, deleteLaw, deleteLawArticle, getLaw,
  updateLawArticle, updateLawBase, updateLawStructure,
} from '../../api/laws'
import { getAnnotationVersionHistory } from '../../api/history'
import type { AnnotationVersionHistory } from '../../types/history'
import type { LawArticle, LawBaseInfo, LawDetail, LawDisplayStatus, LawStructureInput, StructureNodeType, ValidityStatus } from '../../types/law'
import CurrentFormalResult from '../export/CurrentFormalResult.vue'
import LawExportModal from '../export/LawExportModal.vue'
import {
  createLawDetailDraftState,
  mergeLawDetailDraftState,
  type LawDetailDraftState,
  type SavedLawRegion,
} from './lawDetailDraftState'
import {
  nextArticleOrder,
  validateLawArticle,
  validateLawBaseInfo,
  validateLawStructure,
} from './lawImportValidation'

const route = useRoute()
const router = useRouter()
const lawId = computed(() => String(route.params.lawId ?? ''))
const detail = ref<LawDetail | null>(null)
const structures = ref<LawStructureInput[]>([])
const articles = ref<LawArticle[]>([])
const loading = ref(false)
const saving = ref('')
const error = ref('')
const message = ref('')
const formalResult = ref<AnnotationVersionHistory | null>(null)
const formalLoading = ref(false)
const formalError = ref('')
const selectedArticleIds = ref<string[]>([])
const exportOpen = ref(false)
const focusedArticleId = ref('')
const baseValidationIssues = ref<string[]>([])
const structureValidationIssues = ref<string[]>([])
const articleValidationIssues = ref<Record<string, string[]>>({})
const newArticleValidationIssues = ref<string[]>([])
let manualNodeIndex = 0
let draftState: LawDetailDraftState | null = null
let viewGeneration = 0
let loadSequence = 0
let formalSequence = 0

const base = reactive<LawBaseInfo>({ name: '', issuingAuthority: '', publicationDate: '', validityStatus: 'ACTIVE' })
const newArticle = reactive({ number: '', body: '', order: 0 })
const lockedStatuses = new Set<LawDisplayStatus>([
  'ANNOTATING', 'PENDING_REVIEW', 'PARTIALLY_REJECTED', 'PENDING_REREVIEW', 'REVISING',
])
const maintenanceLocked = computed(() => Boolean(detail.value && lockedStatuses.has(detail.value.displayStatus)))
const maintenanceBusy = computed(() => maintenanceLocked.value || Boolean(saving.value))
const allArticlesSelected = computed(() => (
  articles.value.length > 0 && articles.value.every((article) => selectedArticleIds.value.includes(article.articleId))
))
const lockReason = '该法律存在进行中任务，暂不可维护'
const busyReason = '法律维护请求正在处理中，请稍候'
const displayLabels: Record<LawDisplayStatus, string> = {
  UNANNOTATED: '未标注', ANNOTATING: '标注中', PENDING_REVIEW: '待审核',
  PARTIALLY_REJECTED: '部分驳回', PENDING_REREVIEW: '待复审', PENDING_REVISION: '待修订',
  REVISING: '修订中', COMPLETED: '已完成',
}
const validityLabels: Record<ValidityStatus, string> = {
  ACTIVE: '现行有效', NOT_EFFECTIVE: '尚未生效', INVALID: '失效', REPEALED: '已废止',
}

function rejectMaintenanceMutation() {
  if (!maintenanceLocked.value && !saving.value) return false
  error.value = maintenanceLocked.value ? lockReason : busyReason
  message.value = ''
  return true
}

function isCurrentView(targetLawId: string, generation: number): boolean {
  return lawId.value === targetLawId && viewGeneration === generation
}

function resetPageState() {
  detail.value = null
  draftState = null
  structures.value = []
  articles.value = []
  Object.assign(base, {
    name: '', issuingAuthority: '', publicationDate: '', validityStatus: 'ACTIVE',
  })
  Object.assign(newArticle, { number: '', body: '', order: 0 })
  loading.value = false
  saving.value = ''
  error.value = ''
  message.value = ''
  formalResult.value = null
  formalLoading.value = false
  formalError.value = ''
  selectedArticleIds.value = []
  exportOpen.value = false
  focusedArticleId.value = ''
  baseValidationIssues.value = []
  structureValidationIssues.value = []
  articleValidationIssues.value = {}
  newArticleValidationIssues.value = []
  manualNodeIndex = 0
}

function applyDraftState(state: LawDetailDraftState) {
  draftState = state
  detail.value = state.detail
  Object.assign(base, state.base)
  structures.value = state.structures
  articles.value = state.articles
}

function currentDraftState(): LawDetailDraftState {
  if (!draftState || !detail.value) throw new Error('法律详情草稿尚未初始化')
  return {
    ...draftState,
    detail: detail.value,
    base: { ...base },
    structures: structures.value.map((node) => ({ ...node, articleRefs: [...node.articleRefs] })),
    articles: articles.value.map((article) => ({ ...article })),
  }
}

function syncInitial(value: LawDetail) {
  applyDraftState(createLawDetailDraftState(value))
  selectedArticleIds.value = []
  Object.assign(newArticle, { number: '', body: '', order: nextArticleOrder(articles.value) })
}

function syncMutation(value: LawDetail, saved: SavedLawRegion) {
  applyDraftState(mergeLawDetailDraftState(currentDraftState(), value, saved))
  const currentIds = new Set(value.articles.map((article) => article.articleId))
  selectedArticleIds.value = selectedArticleIds.value.filter((articleId) => currentIds.has(articleId))
}

async function loadFormalResult(value: LawDetail, targetLawId: string, generation: number): Promise<void> {
  const currentRequest = ++formalSequence
  formalResult.value = null
  formalError.value = ''
  if (!value.currentAnnotationVersionId) {
    formalLoading.value = false
    return
  }
  formalLoading.value = true
  try {
    const annotation = await getAnnotationVersionHistory(targetLawId, value.currentAnnotationVersionId)
    if (isCurrentView(targetLawId, generation) && currentRequest === formalSequence) {
      formalResult.value = annotation
    }
  } catch (caught: unknown) {
    if (isCurrentView(targetLawId, generation) && currentRequest === formalSequence) {
      formalError.value = apiErrorMessage(caught, '当前正式结果加载失败，请稍后重试')
    }
  } finally {
    if (isCurrentView(targetLawId, generation) && currentRequest === formalSequence) {
      formalLoading.value = false
    }
  }
}

async function focusRouteLocator(): Promise<void> {
  await nextTick()
  const articleId = typeof route.query.articleId === 'string' ? route.query.articleId : ''
  const section = route.query.section === 'formal' ? 'formal' : 'law'
  focusedArticleId.value = articleId && articles.value.some((article) => article.articleId === articleId)
    ? articleId
    : ''
  const targetId = section === 'formal'
    ? (focusedArticleId.value ? `formal-article-${focusedArticleId.value}` : 'formal-results')
    : (focusedArticleId.value ? `law-article-${focusedArticleId.value}` : '')
  if (!targetId) return
  document.getElementById(targetId)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

async function load(targetLawId: string, generation: number) {
  const currentRequest = ++loadSequence
  loading.value = true
  error.value = ''
  try {
    const value = await getLaw(targetLawId)
    if (!isCurrentView(targetLawId, generation) || currentRequest !== loadSequence) return
    if (value.id !== targetLawId) {
      error.value = '法律详情响应与当前路由不一致，请重新加载'
      return
    }
    syncInitial(value)
    await loadFormalResult(value, targetLawId, generation)
  } catch (caught) {
    if (isCurrentView(targetLawId, generation) && currentRequest === loadSequence) {
      error.value = apiErrorMessage(caught)
    }
  } finally {
    if (isCurrentView(targetLawId, generation) && currentRequest === loadSequence) {
      loading.value = false
      void focusRouteLocator()
    }
  }
}

async function run(
  label: string,
  saved: SavedLawRegion,
  operation: (targetLawId: string) => Promise<LawDetail>,
): Promise<boolean> {
  if (rejectMaintenanceMutation()) return false
  const targetLawId = lawId.value
  const generation = viewGeneration
  saving.value = label
  error.value = ''
  message.value = ''
  try {
    const value = await operation(targetLawId)
    if (!isCurrentView(targetLawId, generation)) return false
    if (value.id !== targetLawId) {
      error.value = '法律维护响应与当前路由不一致，请重新加载'
      return false
    }
    syncMutation(value, saved)
    if ((formalResult.value?.annotationVersionId ?? null) !== value.currentAnnotationVersionId) {
      await loadFormalResult(value, targetLawId, generation)
    }
    message.value = '保存成功'
    return true
  } catch (caught) {
    if (isCurrentView(targetLawId, generation)) error.value = apiErrorMessage(caught)
    return false
  } finally {
    if (isCurrentView(targetLawId, generation) && saving.value === label) saving.value = ''
  }
}

async function saveBase() {
  if (rejectMaintenanceMutation()) return
  baseValidationIssues.value = validateLawBaseInfo(base)
  if (baseValidationIssues.value.length) return
  await run('base', { region: 'base' }, (targetLawId) => updateLawBase(targetLawId, { ...base }))
}

async function saveStructure() {
  if (rejectMaintenanceMutation()) return
  structureValidationIssues.value = validateLawStructure(
    structures.value,
    articles.value.map((article) => article.articleId),
  )
  if (structureValidationIssues.value.length) return
  const snapshot = structures.value.map((node) => ({ ...node, articleRefs: [...node.articleRefs] }))
  await run('structure', { region: 'structure' }, (targetLawId) => (
    updateLawStructure(targetLawId, snapshot)
  ))
}

async function saveArticle(article: LawArticle) {
  if (rejectMaintenanceMutation()) return
  const issues = validateLawArticle(article, articles.value)
  articleValidationIssues.value = { ...articleValidationIssues.value, [article.articleId]: issues }
  if (issues.length) return
  const snapshot = { number: article.number, body: article.body, order: article.order }
  const saved = await run(
    `article-${article.articleId}`,
    { region: 'article', articleId: article.articleId },
    (targetLawId) => updateLawArticle(targetLawId, article.articleId, snapshot),
  )
  if (saved) {
    articleValidationIssues.value = { ...articleValidationIssues.value, [article.articleId]: [] }
  }
}

function addStructureNode() {
  if (rejectMaintenanceMutation()) return
  manualNodeIndex += 1
  structures.value.push({
    nodeId: `manual-node-${Date.now()}-${manualNodeIndex}`, type: 'CHAPTER', title: '',
    parentNodeId: null, order: structures.value.length, articleRefs: [],
  })
}

function removeStructureNode(nodeId: string) {
  if (rejectMaintenanceMutation()) return
  structures.value = structures.value.filter((node) => node.nodeId !== nodeId)
  structures.value.forEach((node) => { if (node.parentNodeId === nodeId) node.parentNodeId = null })
}

function toggleAllArticles(): void {
  selectedArticleIds.value = allArticlesSelected.value
    ? []
    : articles.value.map((article) => article.articleId)
}

function openExport(): void {
  if (!detail.value || loading.value) return
  exportOpen.value = true
}

async function createArticle() {
  if (rejectMaintenanceMutation()) return
  newArticleValidationIssues.value = validateLawArticle(
    newArticle,
    [...articles.value, newArticle],
  )
  if (newArticleValidationIssues.value.length) return
  const snapshot = { number: newArticle.number, body: newArticle.body, order: newArticle.order }
  const saved = await run(
    'new-article',
    { region: 'articles' },
    (targetLawId) => addLawArticle(targetLawId, snapshot),
  )
  if (saved) {
    newArticleValidationIssues.value = []
    Object.assign(newArticle, { number: '', body: '', order: nextArticleOrder(articles.value) })
  }
}

async function removeArticle(articleId: string) {
  if (rejectMaintenanceMutation()) return
  if (articles.value.length <= 1) {
    error.value = '法律至少保留一条法条'
    return
  }
  if (!window.confirm('确认删除该法条？系统会保留旧内容版本。')) return
  await run(
    `delete-${articleId}`,
    { region: 'articles' },
    (targetLawId) => deleteLawArticle(targetLawId, articleId),
  )
}

async function removeLaw() {
  if (loading.value || !detail.value) {
    error.value = loading.value ? '法律详情正在加载，请稍候' : '法律详情尚未加载，不能删除'
    return
  }
  if (rejectMaintenanceMutation()) return
  if (!window.confirm('删除后：\n无业务历史的法律将直接永久删除，无法恢复；\n已有业务历史的法律将进入回收站，可恢复。\n确认继续？')) return
  const targetLawId = lawId.value
  const generation = viewGeneration
  saving.value = 'delete-law'
  error.value = ''
  try {
    await deleteLaw(targetLawId)
    if (isCurrentView(targetLawId, generation)) await router.push('/laws')
  } catch (caught) {
    if (isCurrentView(targetLawId, generation)) error.value = apiErrorMessage(caught)
  } finally {
    if (isCurrentView(targetLawId, generation) && saving.value === 'delete-law') saving.value = ''
  }
}

const structureTypes: Array<{ value: StructureNodeType; label: string }> = [
  { value: 'PART', label: '编' }, { value: 'CHAPTER', label: '章' }, { value: 'SECTION', label: '节' },
]

watch(lawId, (targetLawId) => {
  const generation = ++viewGeneration
  ++loadSequence
  ++formalSequence
  resetPageState()
  if (!targetLawId) {
    error.value = '法律 ID 无效'
    return
  }
  void load(targetLawId, generation)
}, { immediate: true })

watch(
  () => [route.query.articleId, route.query.section],
  () => { if (detail.value) void focusRouteLocator() },
)
</script>

<template>
  <section class="law-page">
    <div class="page-title">
      <div><h1>{{ detail?.name || '法律详情' }}</h1><p class="muted">基础信息与当前内容版本维护</p></div>
      <div class="actions"><button :disabled="loading || !detail" @click="openExport">导出</button><RouterLink class="button secondary" :to="{ name: 'law-history', params: { lawId } }">查看历史</RouterLink><RouterLink class="button secondary" to="/laws">返回列表</RouterLink><button class="danger" :disabled="loading || !detail || maintenanceBusy" :title="maintenanceLocked ? lockReason : saving ? busyReason : loading ? '法律详情正在加载' : !detail ? '法律详情尚未加载' : undefined" @click="removeLaw">删除法律</button></div>
    </div>
    <p v-if="error" class="error">{{ error }}</p><p v-if="message" class="notice success">{{ message }}</p>
    <div v-if="loading" class="card empty">正在加载…</div>

    <template v-else-if="detail">
      <div class="status-summary"><span class="badge">{{ validityLabels[detail.validityStatus] }}</span><span class="badge business-status">{{ displayLabels[detail.displayStatus] }}</span></div>
      <p v-if="maintenanceLocked" class="notice lock-notice">{{ lockReason }}</p>
      <p v-if="detail.pendingRevision" class="notice">该法律已有正式标注版本，当前内容变更已记录为待处理修订。</p>
      <div class="card">
        <h2>基础信息</h2>
        <div class="form-grid">
          <label class="field"><span>法律名称</span><input v-model="base.name" :disabled="maintenanceBusy" maxlength="100" /></label>
          <label class="field"><span>发布机关</span><input v-model="base.issuingAuthority" :disabled="maintenanceBusy" maxlength="100" /></label>
          <label class="field"><span>发布日期</span><input v-model="base.publicationDate" :disabled="maintenanceBusy" type="date" /></label>
          <label class="field"><span>效力状态</span><select v-model="base.validityStatus" :disabled="maintenanceBusy"><option value="ACTIVE">现行有效</option><option value="NOT_EFFECTIVE">尚未生效</option><option value="INVALID">失效</option><option value="REPEALED">已废止</option></select></label>
        </div>
        <div v-if="baseValidationIssues.length" class="error"><ul class="issue-list"><li v-for="(issue, index) in baseValidationIssues" :key="`${issue}-${index}`">{{ issue }}</li></ul></div>
        <div class="section-actions"><button :disabled="maintenanceBusy" @click="saveBase">{{ saving === 'base' ? '保存中…' : '保存基础信息' }}</button></div>
      </div>

      <CurrentFormalResult
        :law="detail"
        :annotation="formalResult"
        :articles="articles"
        :loading="formalLoading"
        :error="formalError"
        :focused-article-id="route.query.section === 'formal' ? focusedArticleId : ''"
      />

      <div class="card">
        <div class="row-head"><div><h2>结构</h2><p class="muted">结构调整仅写审计，不生成内容版本。</p></div><button class="secondary small" :disabled="maintenanceBusy" @click="addStructureNode">新增结构</button></div>
        <p v-if="structures.length === 0" class="muted">当前没有编、章、节结构。</p>
        <div v-for="node in structures" :key="node.nodeId" class="row-card">
          <div class="row-head"><strong>{{ node.title || '未命名结构' }}</strong><button class="danger small" :disabled="maintenanceBusy" @click="removeStructureNode(node.nodeId)">移除</button></div>
          <div class="row-grid">
            <label class="field"><span>类型</span><select v-model="node.type" :disabled="maintenanceBusy"><option v-for="item in structureTypes" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
            <label class="field"><span>标题</span><input v-model="node.title" :disabled="maintenanceBusy" maxlength="100" /></label>
            <label class="field"><span>顺序</span><input v-model.number="node.order" :disabled="maintenanceBusy" min="0" step="1" type="number" /></label>
            <label class="field"><span>上级结构</span><select v-model="node.parentNodeId" :disabled="maintenanceBusy"><option :value="null">无</option><option v-for="parent in structures.filter((item) => item.nodeId !== node.nodeId)" :key="parent.nodeId" :value="parent.nodeId">{{ parent.title || parent.nodeId }}</option></select></label>
            <label class="field full"><span>包含法条（可多选）</span><select v-model="node.articleRefs" :disabled="maintenanceBusy" multiple size="4"><option v-for="article in articles" :key="article.articleId" :value="article.articleId">{{ article.number }}</option></select></label>
          </div>
        </div>
        <div v-if="structureValidationIssues.length" class="error"><ul class="issue-list"><li v-for="(issue, index) in structureValidationIssues" :key="`${issue}-${index}`">{{ issue }}</li></ul></div>
        <div class="section-actions"><button :disabled="maintenanceBusy" @click="saveStructure">{{ saving === 'structure' ? '保存中…' : '保存结构' }}</button></div>
      </div>

      <div class="card">
        <div class="row-head"><div><h2>法条</h2><p class="muted">当前内容版本 C{{ detail.currentContentVersionSeq }}；勾选法条可用于部分导出。</p></div><div class="actions"><button class="secondary small" type="button" @click="toggleAllArticles">{{ allArticlesSelected ? '取消全选' : '全选法条' }}</button><span class="badge">已选 {{ selectedArticleIds.length }} / {{ articles.length }} 条</span></div></div>
        <div v-for="article in articles" :id="`law-article-${article.articleId}`" :key="article.articleId" class="row-card law-article-card" :class="{ 'locator-highlight': route.query.section !== 'formal' && focusedArticleId === article.articleId }">
          <div class="row-head"><label class="article-export-checkbox"><input v-model="selectedArticleIds" type="checkbox" :value="article.articleId" /><strong>{{ article.number }}</strong><span>选择导出</span></label><div class="actions"><button class="small" :disabled="maintenanceBusy" @click="saveArticle(article)">保存</button><button class="danger small" :disabled="maintenanceBusy || articles.length <= 1" :title="articles.length <= 1 ? '法律至少保留一条法条' : undefined" @click="removeArticle(article.articleId)">删除</button></div></div>
          <div class="row-grid">
            <label class="field"><span>条号</span><input v-model="article.number" :disabled="maintenanceBusy" maxlength="20" /></label>
            <label class="field"><span>顺序</span><input v-model.number="article.order" :disabled="maintenanceBusy" min="0" step="1" type="number" /></label>
            <label class="field full"><span>正文</span><textarea v-model="article.body" :disabled="maintenanceBusy" rows="5"></textarea></label>
          </div>
          <div v-if="articleValidationIssues[article.articleId]?.length" class="error"><ul class="issue-list"><li v-for="(issue, index) in articleValidationIssues[article.articleId]" :key="`${issue}-${index}`">{{ issue }}</li></ul></div>
        </div>
        <div class="row-card">
          <div class="row-head"><strong>新增法条</strong></div>
          <div class="row-grid">
            <label class="field"><span>条号</span><input v-model="newArticle.number" :disabled="maintenanceBusy" maxlength="20" /></label>
            <label class="field"><span>顺序</span><input v-model.number="newArticle.order" :disabled="maintenanceBusy" min="0" step="1" type="number" /></label>
            <label class="field full"><span>正文</span><textarea v-model="newArticle.body" :disabled="maintenanceBusy" rows="4"></textarea></label>
          </div>
          <div v-if="newArticleValidationIssues.length" class="error"><ul class="issue-list"><li v-for="(issue, index) in newArticleValidationIssues" :key="`${issue}-${index}`">{{ issue }}</li></ul></div>
          <div class="section-actions"><button :disabled="maintenanceBusy || !newArticle.number.trim() || !newArticle.body.trim()" @click="createArticle">{{ saving === 'new-article' ? '添加中…' : '添加法条' }}</button></div>
        </div>
      </div>
    </template>
    <LawExportModal v-if="detail" :open="exportOpen" :law="detail" :selected-article-ids="selectedArticleIds" :annotation="formalResult" @close="exportOpen = false" />
  </section>
</template>

<style src="./law.css"></style>
