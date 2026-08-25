<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import { confirmLaw, parseLaw } from '../../api/laws'
import type { LawImportPreview, LawValidationIssue, StructureNodeType } from '../../types/law'
import { locatorValidationMessage } from '../../utils/errors'
import {
  buildLawImportConfirmPayload,
  currentImportValidationIssues,
  isRecomputableParseIssue,
} from '../../utils/lawImportValidation'

const router = useRouter()
const fullText = ref('')
const preview = ref<LawImportPreview | null>(null)
const parsing = ref(false)
const confirming = ref(false)
const parseError = ref('')
const confirmError = ref('')
const parseWarnings = ref<string[]>([])
const parseValidationIssues = ref<LawValidationIssue[]>([])
let manualIndex = 0

const currentValidationIssues = computed(() => (
  preview.value ? currentImportValidationIssues(preview.value) : []
))
const visibleValidationIssues = computed(() => [
  ...parseValidationIssues.value.filter((issue) => !isRecomputableParseIssue(issue)),
  ...currentValidationIssues.value,
])
const confirmPayload = computed(() => (
  preview.value
    ? buildLawImportConfirmPayload(preview.value, currentValidationIssues.value)
    : null
))
const canConfirm = computed(() => confirmPayload.value !== null)

async function parse() {
  parsing.value = true
  parseError.value = ''
  confirmError.value = ''
  preview.value = null
  parseWarnings.value = []
  parseValidationIssues.value = []
  try {
    const parsed = await parseLaw(fullText.value)
    preview.value = parsed
    parseWarnings.value = [...parsed.warnings]
    parseValidationIssues.value = [...parsed.validationIssues]
  } catch (caught) {
    parseError.value = locatorValidationMessage(caught)
  } finally {
    parsing.value = false
  }
}

function addArticle() {
  if (!preview.value || confirming.value) return
  manualIndex += 1
  preview.value.articles.push({
    clientKey: `manual-article-${Date.now()}-${manualIndex}`,
    number: '', body: '', order: preview.value.articles.length,
  })
}

function removeArticle(clientKey: string) {
  if (!preview.value || confirming.value) return
  preview.value.articles = preview.value.articles.filter((item) => item.clientKey !== clientKey)
  preview.value.structure.forEach((node) => {
    node.articleRefs = node.articleRefs.filter((refKey) => refKey !== clientKey)
  })
}

function addStructure() {
  if (!preview.value || confirming.value) return
  manualIndex += 1
  preview.value.structure.push({
    nodeId: `manual-node-${Date.now()}-${manualIndex}`,
    type: 'CHAPTER', title: '', parentNodeId: null,
    order: preview.value.structure.length, articleRefs: [],
  })
}

function removeStructure(nodeId: string) {
  if (!preview.value || confirming.value) return
  preview.value.structure = preview.value.structure.filter((node) => node.nodeId !== nodeId)
  preview.value.structure.forEach((node) => {
    if (node.parentNodeId === nodeId) node.parentNodeId = null
  })
}

async function confirm() {
  const payload = confirmPayload.value
  if (!payload) return
  confirming.value = true
  confirmError.value = ''
  try {
    const law = await confirmLaw(payload)
    await router.push(`/laws/${law.id}`)
  } catch (caught) {
    confirmError.value = locatorValidationMessage(caught)
  } finally {
    confirming.value = false
  }
}

const structureTypes: Array<{ value: StructureNodeType; label: string }> = [
  { value: 'PART', label: '编' }, { value: 'CHAPTER', label: '章' }, { value: 'SECTION', label: '节' },
]
</script>

<template>
  <section class="law-page">
    <div class="page-title">
      <div><h1>导入法律</h1><p class="muted">粘贴法律全文，解析后核对并确认入库</p></div>
      <RouterLink class="button secondary" to="/laws">返回列表</RouterLink>
    </div>

    <div class="card">
      <h2>1. 粘贴全文</h2>
      <textarea v-model="fullText" rows="14" maxlength="500000" :disabled="parsing || confirming" placeholder="请粘贴一部法律的完整文本"></textarea>
      <div class="section-actions">
        <span class="muted">{{ fullText.length.toLocaleString() }} / 500,000</span>
        <button :disabled="parsing || confirming || !fullText.trim()" @click="parse">{{ parsing ? '解析中…' : '解析并预览' }}</button>
      </div>
      <p v-if="parseError" class="error">{{ parseError }}</p>
    </div>

    <template v-if="preview">
      <div v-if="parseWarnings.length || visibleValidationIssues.length" class="card">
        <h2>解析提示</h2>
        <p v-for="warning in parseWarnings" :key="warning" class="notice">{{ warning }}</p>
        <ul class="issue-list">
          <li v-for="(issue, index) in visibleValidationIssues" :key="`${issue.code}-${issue.field}-${index}`">
            {{ issue.message }}<span v-if="issue.articleNumber">（{{ issue.articleNumber }}）</span>
          </li>
        </ul>
      </div>

      <div class="card">
        <h2>2. 核对基础信息</h2>
        <div class="form-grid">
          <label class="field"><span>法律名称</span><input v-model="preview.baseInfo.name" maxlength="100" :disabled="confirming" /></label>
          <label class="field"><span>发布机关</span><input v-model="preview.baseInfo.issuingAuthority" maxlength="100" :disabled="confirming" /></label>
          <label class="field"><span>发布日期</span><input v-model="preview.baseInfo.publicationDate" type="date" :disabled="confirming" /></label>
          <label class="field"><span>效力状态（需人工确认）</span>
            <select v-model="preview.baseInfo.validityStatus" :disabled="confirming">
              <option :value="null">请选择</option><option value="ACTIVE">现行有效</option>
              <option value="NOT_EFFECTIVE">尚未生效</option><option value="INVALID">失效</option><option value="REPEALED">已废止</option>
            </select>
          </label>
        </div>
      </div>

      <div class="card">
        <div class="row-head"><h2>3. 核对结构</h2><button class="secondary small" :disabled="confirming" @click="addStructure">新增结构</button></div>
        <p v-if="preview.structure.length === 0" class="muted">正文未识别到编、章、节结构，可直接以法条列表导入。</p>
        <div v-for="node in preview.structure" :key="node.nodeId" class="row-card">
          <div class="row-head"><strong>{{ node.title || '未命名结构' }}</strong><button class="danger small" :disabled="confirming" @click="removeStructure(node.nodeId)">移除</button></div>
          <div class="row-grid">
            <label class="field"><span>类型</span><select v-model="node.type" :disabled="confirming"><option v-for="item in structureTypes" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
            <label class="field"><span>标题</span><input v-model="node.title" maxlength="100" :disabled="confirming" /></label>
            <label class="field"><span>顺序</span><input v-model.number="node.order" min="0" max="2147483647" step="1" type="number" :disabled="confirming" /></label>
            <label class="field"><span>上级结构</span><select v-model="node.parentNodeId" :disabled="confirming"><option :value="null">无</option><option v-for="parent in preview.structure.filter((item) => item.nodeId !== node.nodeId)" :key="parent.nodeId" :value="parent.nodeId">{{ parent.title || parent.nodeId }}</option></select></label>
            <label class="field full"><span>包含法条（可多选）</span><select v-model="node.articleRefs" multiple size="4" :disabled="confirming"><option v-for="article in preview.articles" :key="article.clientKey" :value="article.clientKey">{{ article.number || '未编号法条' }}</option></select></label>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="row-head"><h2>4. 核对法条</h2><button class="secondary small" :disabled="confirming" @click="addArticle">新增法条</button></div>
        <div v-for="article in preview.articles" :key="article.clientKey" class="row-card">
          <div class="row-head"><strong>{{ article.number || '未编号法条' }}</strong><button class="danger small" :disabled="confirming" @click="removeArticle(article.clientKey)">移除</button></div>
          <div class="row-grid">
            <label class="field"><span>条号</span><input v-model="article.number" maxlength="20" :disabled="confirming" /></label>
            <label class="field"><span>顺序</span><input v-model.number="article.order" min="0" max="2147483647" step="1" type="number" :disabled="confirming" /></label>
            <label class="field full"><span>正文</span><textarea v-model="article.body" rows="5" :disabled="confirming"></textarea></label>
          </div>
        </div>
        <p v-if="preview.articles.length === 0" class="error">至少需要一条合法法条才能确认导入。</p>
        <div class="section-actions">
          <button :disabled="confirming || !canConfirm" @click="confirm">{{ confirming ? '正在创建…' : '确认导入' }}</button>
        </div>
        <p v-if="confirmError" class="error">{{ confirmError }}</p>
      </div>
    </template>
  </section>
</template>

<style src="./law.css"></style>
