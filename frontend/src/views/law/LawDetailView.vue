<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  addLawArticle, apiErrorMessage, deleteLaw, deleteLawArticle, getLaw,
  updateLawArticle, updateLawBase, updateLawStructure,
} from '../../api/laws'
import type { LawBaseInfo, LawDetail, LawDisplayStatus, LawStructureInput, StructureNodeType, ValidityStatus } from '../../types/law'

const route = useRoute()
const router = useRouter()
const lawId = String(route.params.lawId)
const detail = ref<LawDetail | null>(null)
const structures = ref<LawStructureInput[]>([])
const loading = ref(true)
const saving = ref('')
const error = ref('')
const message = ref('')
let manualNodeIndex = 0

const base = reactive<LawBaseInfo>({ name: '', issuingAuthority: '', publicationDate: '', validityStatus: 'ACTIVE' })
const newArticle = reactive({ number: '', body: '', order: 0 })
const lockedStatuses = new Set<LawDisplayStatus>([
  'ANNOTATING', 'PENDING_REVIEW', 'PARTIALLY_REJECTED', 'PENDING_REREVIEW', 'REVISING',
])
const maintenanceLocked = computed(() => Boolean(detail.value && lockedStatuses.has(detail.value.displayStatus)))
const lockReason = '该法律存在进行中任务，暂不可维护'
const displayLabels: Record<LawDisplayStatus, string> = {
  UNANNOTATED: '未标注', ANNOTATING: '标注中', PENDING_REVIEW: '待审核',
  PARTIALLY_REJECTED: '部分驳回', PENDING_REREVIEW: '待复审', PENDING_REVISION: '待修订',
  REVISING: '修订中', COMPLETED: '已完成',
}
const validityLabels: Record<ValidityStatus, string> = {
  ACTIVE: '现行有效', NOT_EFFECTIVE: '尚未生效', INVALID: '失效', REPEALED: '已废止',
}

function rejectLockedMutation() {
  if (!maintenanceLocked.value) return false
  error.value = lockReason
  message.value = ''
  return true
}

function sync(value: LawDetail) {
  detail.value = value
  Object.assign(base, {
    name: value.name, issuingAuthority: value.issuingAuthority,
    publicationDate: value.publicationDate, validityStatus: value.validityStatus,
  })
  structures.value = value.structure.map((node) => ({
    nodeId: node.nodeId, type: node.type, title: node.title,
    parentNodeId: node.parentNodeId, order: node.order, articleRefs: [...node.articleIds],
  }))
}

async function load() {
  loading.value = true
  error.value = ''
  try { sync(await getLaw(lawId)) } catch (caught) { error.value = apiErrorMessage(caught) }
  finally { loading.value = false }
}

async function run(label: string, operation: () => Promise<LawDetail>) {
  if (rejectLockedMutation()) return
  saving.value = label
  error.value = ''
  message.value = ''
  try {
    sync(await operation())
    message.value = '保存成功'
  } catch (caught) {
    error.value = apiErrorMessage(caught)
  } finally {
    saving.value = ''
  }
}

function addStructureNode() {
  if (rejectLockedMutation()) return
  manualNodeIndex += 1
  structures.value.push({
    nodeId: `manual-node-${Date.now()}-${manualNodeIndex}`, type: 'CHAPTER', title: '',
    parentNodeId: null, order: structures.value.length, articleRefs: [],
  })
}

function removeStructureNode(nodeId: string) {
  if (rejectLockedMutation()) return
  structures.value = structures.value.filter((node) => node.nodeId !== nodeId)
  structures.value.forEach((node) => { if (node.parentNodeId === nodeId) node.parentNodeId = null })
}

async function createArticle() {
  if (rejectLockedMutation()) return
  await run('new-article', () => addLawArticle(lawId, newArticle))
  if (!error.value) Object.assign(newArticle, { number: '', body: '', order: detail.value?.articles.length || 0 })
}

async function removeArticle(articleId: string) {
  if (rejectLockedMutation()) return
  if (!window.confirm('确认删除该法条？系统会保留旧内容版本。')) return
  await run(`delete-${articleId}`, () => deleteLawArticle(lawId, articleId))
}

async function removeLaw() {
  if (rejectLockedMutation()) return
  if (!window.confirm('确认将这部法律移入回收站？')) return
  saving.value = 'delete-law'
  error.value = ''
  try { await deleteLaw(lawId); await router.push('/laws') }
  catch (caught) { error.value = apiErrorMessage(caught); saving.value = '' }
}

const structureTypes: Array<{ value: StructureNodeType; label: string }> = [
  { value: 'PART', label: '编' }, { value: 'CHAPTER', label: '章' }, { value: 'SECTION', label: '节' },
]

onMounted(load)
</script>

<template>
  <section class="law-page">
    <div class="page-title">
      <div><h1>{{ detail?.name || '法律详情' }}</h1><p class="muted">基础信息与当前内容版本维护</p></div>
      <div class="actions"><RouterLink class="button secondary" to="/laws">返回列表</RouterLink><button class="danger" :disabled="!!saving || maintenanceLocked" :title="maintenanceLocked ? lockReason : undefined" @click="removeLaw">删除法律</button></div>
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
          <label class="field"><span>法律名称</span><input v-model="base.name" :disabled="maintenanceLocked" maxlength="100" /></label>
          <label class="field"><span>发布机关</span><input v-model="base.issuingAuthority" :disabled="maintenanceLocked" maxlength="100" /></label>
          <label class="field"><span>发布日期</span><input v-model="base.publicationDate" :disabled="maintenanceLocked" type="date" /></label>
          <label class="field"><span>效力状态</span><select v-model="base.validityStatus" :disabled="maintenanceLocked"><option value="ACTIVE">现行有效</option><option value="NOT_EFFECTIVE">尚未生效</option><option value="INVALID">失效</option><option value="REPEALED">已废止</option></select></label>
        </div>
        <div class="section-actions"><button :disabled="!!saving || maintenanceLocked" @click="run('base', () => updateLawBase(lawId, base))">{{ saving === 'base' ? '保存中…' : '保存基础信息' }}</button></div>
      </div>

      <div class="card">
        <div class="row-head"><div><h2>结构</h2><p class="muted">结构调整仅写审计，不生成内容版本。</p></div><button class="secondary small" :disabled="maintenanceLocked" @click="addStructureNode">新增结构</button></div>
        <p v-if="structures.length === 0" class="muted">当前没有编、章、节结构。</p>
        <div v-for="node in structures" :key="node.nodeId" class="row-card">
          <div class="row-head"><strong>{{ node.title || '未命名结构' }}</strong><button class="danger small" :disabled="maintenanceLocked" @click="removeStructureNode(node.nodeId)">移除</button></div>
          <div class="row-grid">
            <label class="field"><span>类型</span><select v-model="node.type" :disabled="maintenanceLocked"><option v-for="item in structureTypes" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
            <label class="field"><span>标题</span><input v-model="node.title" :disabled="maintenanceLocked" maxlength="100" /></label>
            <label class="field"><span>顺序</span><input v-model.number="node.order" :disabled="maintenanceLocked" min="0" type="number" /></label>
            <label class="field"><span>上级结构</span><select v-model="node.parentNodeId" :disabled="maintenanceLocked"><option :value="null">无</option><option v-for="parent in structures.filter((item) => item.nodeId !== node.nodeId)" :key="parent.nodeId" :value="parent.nodeId">{{ parent.title || parent.nodeId }}</option></select></label>
            <label class="field full"><span>包含法条（可多选）</span><select v-model="node.articleRefs" :disabled="maintenanceLocked" multiple size="4"><option v-for="article in detail.articles" :key="article.articleId" :value="article.articleId">{{ article.number }}</option></select></label>
          </div>
        </div>
        <div class="section-actions"><button :disabled="!!saving || maintenanceLocked" @click="run('structure', () => updateLawStructure(lawId, structures))">{{ saving === 'structure' ? '保存中…' : '保存结构' }}</button></div>
      </div>

      <div class="card">
        <div class="row-head"><div><h2>法条</h2><p class="muted">当前内容版本 C{{ detail.currentContentVersionSeq }}；法条语义变更会追加新版本。</p></div><span class="badge">{{ detail.articles.length }} 条</span></div>
        <div v-for="article in detail.articles" :key="article.articleId" class="row-card">
          <div class="row-head"><strong>{{ article.number }}</strong><div class="actions"><button class="small" :disabled="!!saving || maintenanceLocked" @click="run(`article-${article.articleId}`, () => updateLawArticle(lawId, article.articleId, article))">保存</button><button class="danger small" :disabled="!!saving || maintenanceLocked" @click="removeArticle(article.articleId)">删除</button></div></div>
          <div class="row-grid">
            <label class="field"><span>条号</span><input v-model="article.number" :disabled="maintenanceLocked" maxlength="50" /></label>
            <label class="field"><span>顺序</span><input v-model.number="article.order" :disabled="maintenanceLocked" min="0" type="number" /></label>
            <label class="field full"><span>正文</span><textarea v-model="article.body" :disabled="maintenanceLocked" rows="5"></textarea></label>
          </div>
        </div>
        <div class="row-card">
          <div class="row-head"><strong>新增法条</strong></div>
          <div class="row-grid">
            <label class="field"><span>条号</span><input v-model="newArticle.number" :disabled="maintenanceLocked" maxlength="50" /></label>
            <label class="field"><span>顺序</span><input v-model.number="newArticle.order" :disabled="maintenanceLocked" min="0" type="number" /></label>
            <label class="field full"><span>正文</span><textarea v-model="newArticle.body" :disabled="maintenanceLocked" rows="4"></textarea></label>
          </div>
          <div class="section-actions"><button :disabled="!!saving || maintenanceLocked || !newArticle.number.trim() || !newArticle.body.trim()" @click="createArticle">{{ saving === 'new-article' ? '添加中…' : '添加法条' }}</button></div>
        </div>
      </div>
    </template>
  </section>
</template>

<style src="./law.css"></style>
