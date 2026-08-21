<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import { apiErrorMessage, confirmLaw, parseLaw } from '../../api/laws'
import type { LawImportPreview, StructureNodeType } from '../../types/law'

const router = useRouter()
const fullText = ref('')
const preview = ref<LawImportPreview | null>(null)
const parsing = ref(false)
const confirming = ref(false)
const error = ref('')
let manualIndex = 0

const canConfirm = computed(() => {
  const value = preview.value
  if (!value) return false
  const base = value.baseInfo
  return Boolean(
    base.name?.trim() && base.issuingAuthority?.trim() && base.publicationDate && base.validityStatus
      && value.articles.length > 0
      && value.articles.every((article) => article.number.trim() && article.body.trim()),
  )
})

async function parse() {
  parsing.value = true
  error.value = ''
  preview.value = null
  try {
    preview.value = await parseLaw(fullText.value)
  } catch (caught) {
    error.value = apiErrorMessage(caught)
  } finally {
    parsing.value = false
  }
}

function addArticle() {
  if (!preview.value) return
  manualIndex += 1
  preview.value.articles.push({
    clientKey: `manual-article-${Date.now()}-${manualIndex}`,
    number: '', body: '', order: preview.value.articles.length,
  })
}

function removeArticle(clientKey: string) {
  if (!preview.value) return
  preview.value.articles = preview.value.articles.filter((item) => item.clientKey !== clientKey)
  preview.value.structure.forEach((node) => {
    node.articleRefs = node.articleRefs.filter((refKey) => refKey !== clientKey)
  })
}

function addStructure() {
  if (!preview.value) return
  manualIndex += 1
  preview.value.structure.push({
    nodeId: `manual-node-${Date.now()}-${manualIndex}`,
    type: 'CHAPTER', title: '', parentNodeId: null,
    order: preview.value.structure.length, articleRefs: [],
  })
}

function removeStructure(nodeId: string) {
  if (!preview.value) return
  preview.value.structure = preview.value.structure.filter((node) => node.nodeId !== nodeId)
  preview.value.structure.forEach((node) => {
    if (node.parentNodeId === nodeId) node.parentNodeId = null
  })
}

async function confirm() {
  if (!preview.value || !canConfirm.value) return
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
      <RouterLink class="button secondary" to="/laws">返回列表</RouterLink>
    </div>

    <div class="card">
      <h2>1. 粘贴全文</h2>
      <textarea v-model="fullText" rows="14" maxlength="500000" placeholder="请粘贴一部法律的完整文本"></textarea>
      <div class="section-actions">
        <span class="muted">{{ fullText.length.toLocaleString() }} / 500,000</span>
        <button :disabled="parsing || !fullText.trim()" @click="parse">{{ parsing ? '解析中…' : '解析并预览' }}</button>
      </div>
      <p v-if="error" class="error">{{ error }}</p>
    </div>

    <template v-if="preview">
      <div v-if="preview.warnings.length || preview.validationIssues.length" class="card">
        <h2>解析提示</h2>
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
          <label class="field"><span>法律名称</span><input v-model="preview.baseInfo.name" maxlength="100" /></label>
          <label class="field"><span>发布机关</span><input v-model="preview.baseInfo.issuingAuthority" maxlength="100" /></label>
          <label class="field"><span>发布日期</span><input v-model="preview.baseInfo.publicationDate" type="date" /></label>
          <label class="field"><span>效力状态（需人工确认）</span>
            <select v-model="preview.baseInfo.validityStatus">
              <option :value="null">请选择</option><option value="ACTIVE">现行有效</option>
              <option value="NOT_EFFECTIVE">尚未生效</option><option value="INVALID">失效</option><option value="REPEALED">已废止</option>
            </select>
          </label>
        </div>
      </div>

      <div class="card">
        <div class="row-head"><h2>3. 核对结构</h2><button class="secondary small" @click="addStructure">新增结构</button></div>
        <p v-if="preview.structure.length === 0" class="muted">正文未识别到编、章、节结构，可直接以法条列表导入。</p>
        <div v-for="node in preview.structure" :key="node.nodeId" class="row-card">
          <div class="row-head"><strong>{{ node.title || '未命名结构' }}</strong><button class="danger small" @click="removeStructure(node.nodeId)">移除</button></div>
          <div class="row-grid">
            <label class="field"><span>类型</span><select v-model="node.type"><option v-for="item in structureTypes" :key="item.value" :value="item.value">{{ item.label }}</option></select></label>
            <label class="field"><span>标题</span><input v-model="node.title" maxlength="100" /></label>
            <label class="field"><span>顺序</span><input v-model.number="node.order" min="0" type="number" /></label>
            <label class="field"><span>上级结构</span><select v-model="node.parentNodeId"><option :value="null">无</option><option v-for="parent in preview.structure.filter((item) => item.nodeId !== node.nodeId)" :key="parent.nodeId" :value="parent.nodeId">{{ parent.title || parent.nodeId }}</option></select></label>
            <label class="field full"><span>包含法条（可多选）</span><select v-model="node.articleRefs" multiple size="4"><option v-for="article in preview.articles" :key="article.clientKey" :value="article.clientKey">{{ article.number || '未编号法条' }}</option></select></label>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="row-head"><h2>4. 核对法条</h2><button class="secondary small" @click="addArticle">新增法条</button></div>
        <div v-for="article in preview.articles" :key="article.clientKey" class="row-card">
          <div class="row-head"><strong>{{ article.number || '未编号法条' }}</strong><button class="danger small" @click="removeArticle(article.clientKey)">移除</button></div>
          <div class="row-grid">
            <label class="field"><span>条号</span><input v-model="article.number" maxlength="50" /></label>
            <label class="field"><span>顺序</span><input v-model.number="article.order" min="0" type="number" /></label>
            <label class="field full"><span>正文</span><textarea v-model="article.body" rows="5"></textarea></label>
          </div>
        </div>
        <p v-if="preview.articles.length === 0" class="error">至少需要一条合法法条才能确认导入。</p>
        <div class="section-actions">
          <button :disabled="confirming || !canConfirm" @click="confirm">{{ confirming ? '正在创建…' : '确认导入' }}</button>
        </div>
      </div>
    </template>
  </section>
</template>

<style src="./law.css"></style>
