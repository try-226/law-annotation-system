<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import { apiErrorMessage, confirmLaw, parseLaw } from '../../api/laws'
import type { LawImportPreview, StructureNodeType } from '../../types/law'
import {
  buildPasteCandidate,
  FULL_TEXT_TOO_LONG_MESSAGE,
  fullTextCodePointLength,
  nextArticleOrder,
  validateFullTextLength,
  validateLawImportPreview,
} from './lawImportValidation'

const router = useRouter()
const fullText = ref('')
const preview = ref<LawImportPreview | null>(null)
const parsedSourceSnapshot = ref<string | null>(null)
const stage = ref<'input' | 'preview'>('input')
const parsing = ref(false)
const confirming = ref(false)
const error = ref('')
let manualIndex = 0

const busy = computed(() => parsing.value || confirming.value)
const fullTextLength = computed(() => fullTextCodePointLength(fullText.value))
const previewIsStale = computed(() => (
  preview.value !== null && parsedSourceSnapshot.value !== fullText.value
))
const currentValidationIssues = computed(() => {
  const value = preview.value
  if (!value) return ['尚未生成预览']
  return validateLawImportPreview(value)
})
const canConfirm = computed(() => !previewIsStale.value && currentValidationIssues.value.length === 0)

async function parse() {
  if (busy.value) return
  const source = fullText.value
  const lengthError = validateFullTextLength(source)
  if (lengthError) {
    error.value = lengthError
    return
  }
  parsing.value = true
  error.value = ''
  try {
    preview.value = await parseLaw(source)
    parsedSourceSnapshot.value = source
    stage.value = 'preview'
  } catch (caught) {
    error.value = apiErrorMessage(caught)
  } finally {
    parsing.value = false
  }
}

function handleFullTextPaste(event: ClipboardEvent) {
  const clipboardData = event.clipboardData
  const textarea = event.currentTarget as HTMLTextAreaElement
  if (!clipboardData) return
  const candidate = buildPasteCandidate(
    fullText.value,
    clipboardData.getData('text'),
    textarea.selectionStart,
    textarea.selectionEnd,
  )
  const lengthError = validateFullTextLength(candidate)
  if (!lengthError) {
    if (error.value === FULL_TEXT_TOO_LONG_MESSAGE) error.value = ''
    return
  }
  event.preventDefault()
  error.value = lengthError
}

function handleFullTextInput() {
  if (error.value === FULL_TEXT_TOO_LONG_MESSAGE && !validateFullTextLength(fullText.value)) {
    error.value = ''
  }
}

function returnToInput() {
  if (busy.value) return
  stage.value = 'input'
  error.value = ''
}

function returnToPreview() {
  if (busy.value || previewIsStale.value) return
  stage.value = 'preview'
}

function returnToList() {
  if (busy.value) return
  void router.push('/laws')
}

function addArticle() {
  if (busy.value || !preview.value) return
  manualIndex += 1
  preview.value.articles.push({
    clientKey: `manual-article-${Date.now()}-${manualIndex}`,
    number: '', body: '', order: nextArticleOrder(preview.value.articles),
  })
}

function removeArticle(clientKey: string) {
  if (busy.value || !preview.value) return
  preview.value.articles = preview.value.articles.filter((item) => item.clientKey !== clientKey)
  preview.value.structure.forEach((node) => {
    node.articleRefs = node.articleRefs.filter((refKey) => refKey !== clientKey)
  })
}

function addStructure() {
  if (busy.value || !preview.value) return
  manualIndex += 1
  preview.value.structure.push({
    nodeId: `manual-node-${Date.now()}-${manualIndex}`,
    type: 'CHAPTER', title: '', parentNodeId: null,
    order: preview.value.structure.length, articleRefs: [],
  })
}

function removeStructure(nodeId: string) {
  if (busy.value || !preview.value) return
  preview.value.structure = preview.value.structure.filter((node) => node.nodeId !== nodeId)
  preview.value.structure.forEach((node) => {
    if (node.parentNodeId === nodeId) node.parentNodeId = null
  })
}

async function confirm() {
  if (busy.value || !preview.value || !canConfirm.value) return
  confirming.value = true
  error.value = ''
  try {
    const law = await confirmLaw(preview.value)
    await router.push(`/laws/${law.id}`)
  } catch (caught) {
    error.value = apiErrorMessage(caught)
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
      <button class="secondary" :disabled="busy" @click="returnToList">返回列表</button>
    </div>

    <div v-if="stage === 'input'" class="card">
      <h2>1. 粘贴全文</h2>
      <textarea v-model="fullText" :disabled="busy" rows="14" placeholder="请粘贴一部法律的完整文本" @input="handleFullTextInput" @paste="handleFullTextPaste"></textarea>
      <p v-if="previewIsStale" class="notice">全文已修改，现有解析预览已过期，请重新解析。</p>
      <div class="section-actions">
        <span class="muted">{{ fullTextLength.toLocaleString() }} / 500,000</span>
        <div class="actions">
          <button v-if="preview" class="secondary" :disabled="busy || previewIsStale" @click="returnToPreview">返回已有预览</button>
          <button :disabled="busy || !fullText.trim()" @click="parse">{{ parsing ? '解析中…' : '解析并预览' }}</button>
        </div>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
    </div>

    <template v-if="stage === 'preview' && preview">
      <div class="preview-heading"><div><strong>解析预览</strong><span class="muted">请按当前内容核对后确认</span></div><button class="secondary small" :disabled="busy" @click="returnToInput">返回全文</button></div>
      <p v-if="error" class="error">{{ error }}</p>
      <div v-if="preview.warnings.length || preview.validationIssues.length" class="card">
        <h2>识别时诊断</h2>
        <p class="muted">以下内容记录解析当时的问题。修正当前预览后，不会继续阻止确认。</p>
        <p v-for="warning in preview.warnings" :key="warning" class="notice">{{ warning }}</p>
        <ul class="issue-list">
          <li v-for="(issue, index) in preview.validationIssues" :key="`${issue.code}-${index}`">
            {{ issue.message }}<span v-if="issue.articleNumber">（{{ issue.articleNumber }}）</span>
          </li>
        </ul>
      </div>

      <div class="card">
        <h2>2. 核对基础信息</h2>
        <div class="form-grid">
          <label class="field"><span>法律名称</span><input v-model="preview.baseInfo.name" :disabled="busy" maxlength="100" /></label>
          <label class="field"><span>发布机关</span><input v-model="preview.baseInfo.issuingAuthority" :disabled="busy" maxlength="100" /></label>
          <label class="field"><span>发布日期</span><input v-model="preview.baseInfo.publicationDate" :disabled="busy" type="date" /></label>
          <label class="field"><span>效力状态（需人工确认）</span>
            <select v-model="preview.baseInfo.validityStatus" :disabled="busy">
              <option :value="null">请选择</option><option value="ACTIVE">现行有效</option>
              <option value="NOT_EFFECTIVE">尚未生效</option><option value="INVALID">失效</option><option value="REPEALED">已废止</option>
            </select>
          </label>
        </div>
      </div>

      <div class="card">
        <div class="row-head"><h2>3. 核对结构</h2><button class="secondary small" :disabled="busy" @click="addStructure">新增结构</button></div>
        <p v-if="preview.structure.length === 0" class="muted">正文未识别到编、章、节结构，可直接以法条列表导入。</p>
        <div v-for="node in preview.structure" :key="node.nodeId" class="row-card">
          <div class="row-head"><strong>{{ node.title || '未命名结构' }}</strong><button class="danger small" :disabled="busy" @click="removeStructure(node.nodeId)">移除</button></div>
          <div class="row-grid">
            <label class="field"><span>类型</span><select v-model="node.type" :disabled="busy"><option v-for="item in structureTypes" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
            <label class="field"><span>标题</span><input v-model="node.title" :disabled="busy" maxlength="100" /></label>
            <label class="field"><span>顺序</span><input v-model.number="node.order" :disabled="busy" min="0" type="number" /></label>
            <label class="field"><span>上级结构</span><select v-model="node.parentNodeId" :disabled="busy"><option :value="null">无</option><option v-for="parent in preview.structure.filter((item) => item.nodeId !== node.nodeId)" :key="parent.nodeId" :value="parent.nodeId">{{ parent.title || parent.nodeId }}</option></select></label>
            <label class="field full"><span>包含法条（可多选）</span><select v-model="node.articleRefs" :disabled="busy" multiple size="4"><option v-for="article in preview.articles" :key="article.clientKey" :value="article.clientKey">{{ article.number || '未编号法条' }}</option></select></label>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="row-head"><h2>4. 核对法条</h2><button class="secondary small" :disabled="busy" @click="addArticle">新增法条</button></div>
        <div v-for="article in preview.articles" :key="article.clientKey" class="row-card">
          <div class="row-head"><strong>{{ article.number || '未编号法条' }}</strong><button class="danger small" :disabled="busy" @click="removeArticle(article.clientKey)">移除</button></div>
          <div class="row-grid">
            <label class="field"><span>条号</span><input v-model="article.number" :disabled="busy" maxlength="20" /></label>
            <label class="field"><span>顺序</span><input v-model.number="article.order" :disabled="busy" min="0" type="number" /></label>
            <label class="field full"><span>正文</span><textarea v-model="article.body" :disabled="busy" rows="5"></textarea></label>
          </div>
        </div>
        <p v-if="preview.articles.length === 0" class="error">至少需要一条合法法条才能确认导入。</p>
        <div v-if="currentValidationIssues.length" class="error">
          <strong>当前预览仍需修正：</strong>
          <ul class="issue-list"><li v-for="issue in currentValidationIssues" :key="issue">{{ issue }}</li></ul>
        </div>
        <div class="section-actions">
          <button :disabled="busy || !canConfirm" @click="confirm">{{ confirming ? '正在创建…' : '确认导入' }}</button>
        </div>
      </div>
    </template>
  </section>
</template>

<style src="./law.css"></style>
