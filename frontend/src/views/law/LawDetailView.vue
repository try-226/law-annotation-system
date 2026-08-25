<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import {
  addLawArticle,
  deleteLaw,
  deleteLawArticle,
  getLaw,
  updateLawArticle,
  updateLawBase,
  updateLawStructure,
} from '../../api/laws'
import { listTasks } from '../../api/tasks'
import type {
  LawArticle,
  LawBaseInfo,
  LawDetail,
  LawDisplayStatus,
  LawStructureInput,
  LawStructureNode,
  StructureNodeType,
  ValidityStatus,
} from '../../types/law'
import {
  isUnfinishedTaskState,
  TASK_STATE_LABELS,
  TASK_TYPE_LABELS,
  type TaskListItem,
} from '../../types/task'
import { formatDateTimeToMinute } from '../../utils/dateTime'
import { locatorValidationMessage } from '../../utils/errors'

interface TreeRow {
  node: LawStructureNode
  depth: number
  hasChildren: boolean
}

interface ArticleEditBuffer {
  articleId: string
  number: string
  body: string
  order: number
}

const route = useRoute()
const router = useRouter()
const lawId = String(route.params.lawId)
const detail = ref<LawDetail | null>(null)
const tasks = ref<TaskListItem[]>([])
const structures = ref<LawStructureInput[]>([])
const loading = ref(true)
const saving = ref('')
const error = ref('')
const message = ref('')
const expandedNodeIds = ref<Set<string>>(new Set())
const selectedNodeId = ref<string | null>(null)
const articleEdit = ref<ArticleEditBuffer | null>(null)
let manualNodeIndex = 0

const base = reactive<LawBaseInfo>({
  name: '',
  issuingAuthority: '',
  publicationDate: '',
  validityStatus: 'ACTIVE',
})
const newArticle = reactive({ number: '', body: '', order: 0 })

const validityLabels: Record<ValidityStatus, string> = {
  ACTIVE: '现行有效',
  NOT_EFFECTIVE: '尚未生效',
  INVALID: '失效',
  REPEALED: '已废止',
}

const structureTypeLabels: Record<StructureNodeType, string> = {
  PART: '编',
  CHAPTER: '章',
  SECTION: '节',
}

const structureTypes: Array<{ value: StructureNodeType; label: string }> = [
  { value: 'PART', label: '编' },
  { value: 'CHAPTER', label: '章' },
  { value: 'SECTION', label: '节' },
]

const displayStatusLabels: Record<LawDisplayStatus, string> = {
  UNANNOTATED: '未标注',
  PENDING_ANNOTATION: '待标注',
  ANNOTATING: '标注中',
  PENDING_REVIEW: '待审核',
  PARTIALLY_REJECTED: '部分驳回',
  PENDING_REREVIEW: '待复审',
  COMPLETED: '已完成',
  PENDING_REVISION: '待修订',
  REVISING: '修订中',
}
const activeTaskDisplayStatuses: ReadonlySet<LawDisplayStatus> = new Set([
  'PENDING_ANNOTATION',
  'ANNOTATING',
  'PENDING_REVIEW',
  'PARTIALLY_REJECTED',
  'PENDING_REREVIEW',
  'REVISING',
])

const currentTask = computed(() => (
  tasks.value.find((task) => isUnfinishedTaskState(task.taskState)) ?? null
))
const latestApprovedTask = computed(() => (
  tasks.value.find((task) => task.taskState === 'APPROVED') ?? null
))
const hasActiveTask = computed(() => (
  currentTask.value !== null
  || (detail.value !== null && activeTaskDisplayStatuses.has(detail.value.displayStatus))
))
const mutationBusy = computed(() => hasActiveTask.value || Boolean(saving.value))
const maintenanceDisabled = computed(() => mutationBusy.value || articleEdit.value !== null)
const lockReason = computed(() => (
  hasActiveTask.value
    ? '该法律存在未结束任务，基础信息、结构、法条和删除操作均已锁定。'
    : ''
))

const nodeById = computed(() => new Map(
  (detail.value?.structure ?? []).map((node) => [node.nodeId, node]),
))

const childNodes = computed(() => {
  const children = new Map<string | null, LawStructureNode[]>()
  for (const node of detail.value?.structure ?? []) {
    const parentId = node.parentNodeId && nodeById.value.has(node.parentNodeId)
      ? node.parentNodeId
      : null
    const siblings = children.get(parentId) ?? []
    siblings.push(node)
    children.set(parentId, siblings)
  }
  for (const siblings of children.values()) {
    siblings.sort((left, right) => left.order - right.order)
  }
  return children
})

const treeRows = computed<TreeRow[]>(() => {
  const rows: TreeRow[] = []
  const visited = new Set<string>()
  const walk = (node: LawStructureNode, depth: number) => {
    if (visited.has(node.nodeId)) return
    visited.add(node.nodeId)
    const children = childNodes.value.get(node.nodeId) ?? []
    rows.push({ node, depth, hasChildren: children.length > 0 })
    if (expandedNodeIds.value.has(node.nodeId)) {
      children.forEach((child) => walk(child, depth + 1))
    }
  }
  for (const node of childNodes.value.get(null) ?? []) walk(node, 0)
  for (const node of detail.value?.structure ?? []) {
    if (!visited.has(node.nodeId)) walk(node, 0)
  }
  return rows
})

const selectedArticleIds = computed<Set<string> | null>(() => {
  if (!selectedNodeId.value) return null
  const articleIds = new Set<string>()
  const visited = new Set<string>()
  const collect = (nodeId: string) => {
    if (visited.has(nodeId)) return
    visited.add(nodeId)
    nodeById.value.get(nodeId)?.articleIds.forEach((articleId) => articleIds.add(articleId))
    for (const child of childNodes.value.get(nodeId) ?? []) collect(child.nodeId)
  }
  collect(selectedNodeId.value)
  return articleIds
})

const displayedArticles = computed(() => {
  const articles = detail.value?.articles ?? []
  return selectedArticleIds.value === null
    ? articles
    : articles.filter((article) => selectedArticleIds.value?.has(article.articleId))
})

const selectedStructureTitle = computed(() => (
  selectedNodeId.value
    ? nodeById.value.get(selectedNodeId.value)?.title ?? '所选结构'
    : '全部法条'
))

const displayStatus = computed(() => (
  detail.value ? displayStatusLabels[detail.value.displayStatus] : ''
))

function sync(value: LawDetail, resetTree = false) {
  detail.value = value
  Object.assign(base, {
    name: value.name,
    issuingAuthority: value.issuingAuthority,
    publicationDate: value.publicationDate,
    validityStatus: value.validityStatus,
  })
  structures.value = value.structure.map((node) => ({
    nodeId: node.nodeId,
    type: node.type,
    title: node.title,
    parentNodeId: node.parentNodeId,
    order: node.order,
    articleRefs: [...node.articleIds],
  }))
  if (resetTree) {
    expandedNodeIds.value = new Set(value.structure.map((node) => node.nodeId))
  }
}

async function load() {
  loading.value = true
  error.value = ''
  detail.value = null
  tasks.value = []
  articleEdit.value = null
  try {
    const [law, taskPage] = await Promise.all([
      getLaw(lawId),
      listTasks({ lawId, page: 0, size: 100 }),
    ])
    tasks.value = taskPage.items
    sync(law, true)
  } catch (caught) {
    error.value = locatorValidationMessage(caught, '法律详情加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function run(label: string, operation: () => Promise<LawDetail>) {
  if (hasActiveTask.value) {
    error.value = lockReason.value
    return
  }
  saving.value = label
  error.value = ''
  message.value = ''
  try {
    sync(await operation())
    message.value = '保存成功'
  } catch (caught) {
    error.value = locatorValidationMessage(caught)
  } finally {
    saving.value = ''
  }
}

function addStructureNode() {
  if (maintenanceDisabled.value) return
  manualNodeIndex += 1
  structures.value.push({
    nodeId: 'manual-node-' + Date.now() + '-' + manualNodeIndex,
    type: 'CHAPTER',
    title: '',
    parentNodeId: null,
    order: structures.value.length,
    articleRefs: [],
  })
}

function removeStructureNode(nodeId: string) {
  if (maintenanceDisabled.value) return
  structures.value = structures.value.filter((node) => node.nodeId !== nodeId)
  structures.value.forEach((node) => {
    if (node.parentNodeId === nodeId) node.parentNodeId = null
  })
}

async function createArticle() {
  await run('new-article', () => addLawArticle(lawId, newArticle))
  if (!error.value) {
    Object.assign(newArticle, {
      number: '',
      body: '',
      order: detail.value?.articles.length ?? 0,
    })
  }
}

async function removeArticle(articleId: string) {
  if (maintenanceDisabled.value) return
  if (!window.confirm('确认删除该法条？系统会保留旧内容版本。')) return
  await run('delete-' + articleId, () => deleteLawArticle(lawId, articleId))
}

function startArticleEdit(article: LawArticle) {
  if (maintenanceDisabled.value) return
  articleEdit.value = {
    articleId: article.articleId,
    number: article.number,
    body: article.body,
    order: article.order,
  }
  error.value = ''
  message.value = ''
}

function cancelArticleEdit() {
  if (saving.value) return
  articleEdit.value = null
  error.value = ''
}

async function saveArticleEdit() {
  const buffer = articleEdit.value
  if (!buffer || mutationBusy.value) return
  if (hasActiveTask.value) {
    error.value = lockReason.value
    return
  }
  const label = 'article-' + buffer.articleId
  saving.value = label
  error.value = ''
  message.value = ''
  try {
    const updated = await updateLawArticle(lawId, buffer.articleId, {
      number: buffer.number,
      body: buffer.body,
      order: buffer.order,
    })
    sync(updated)
    articleEdit.value = null
    message.value = '保存成功'
  } catch (caught) {
    error.value = locatorValidationMessage(caught)
  } finally {
    saving.value = ''
  }
}

async function removeLaw() {
  if (maintenanceDisabled.value) return
  if (!window.confirm('确认删除这部法律？有历史数据时将进入回收站。')) return
  saving.value = 'delete-law'
  error.value = ''
  try {
    await deleteLaw(lawId)
    await router.push({ name: 'law-list' })
  } catch (caught) {
    error.value = locatorValidationMessage(caught)
    saving.value = ''
  }
}

function toggleNode(nodeId: string) {
  const next = new Set(expandedNodeIds.value)
  if (next.has(nodeId)) next.delete(nodeId)
  else next.add(nodeId)
  expandedNodeIds.value = next
}

function articlePath(articleId: string) {
  const candidates = (detail.value?.structure ?? [])
    .filter((node) => node.articleIds.includes(articleId))
    .map((node) => {
      const titles: string[] = []
      const visited = new Set<string>()
      let current: LawStructureNode | undefined = node
      while (current && !visited.has(current.nodeId)) {
        visited.add(current.nodeId)
        titles.unshift(current.title)
        current = current.parentNodeId ? nodeById.value.get(current.parentNodeId) : undefined
      }
      return titles
    })
    .sort((left, right) => right.length - left.length)
  return candidates[0]?.join(' / ') || '未归入章节'
}

onMounted(() => { void load() })
</script>

<template>
  <section class="law-page law-detail-page">
    <div class="page-title">
      <div>
        <p class="eyebrow">法律条文管理 / 法律详情</p>
        <h1>{{ detail?.name || '法律详情' }}</h1>
        <p class="muted">查看并维护基础信息、章节结构和当前法条内容</p>
      </div>
      <div class="actions">
        <RouterLink class="button secondary" :to="{ name: 'law-list' }">返回法律列表</RouterLink>
        <button
          class="danger"
          type="button"
          :disabled="loading || !detail || maintenanceDisabled"
          :title="lockReason"
          @click="removeLaw"
        >
          {{ saving === 'delete-law' ? '删除中…' : '删除法律' }}
        </button>
      </div>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="message" class="notice success">{{ message }}</p>
    <div v-if="loading" class="card empty">正在加载法律详情…</div>
    <div v-else-if="!detail" class="card empty error-state">
      <button class="secondary small" type="button" @click="load">重试加载</button>
    </div>

    <template v-else-if="detail">
      <p v-if="hasActiveTask" class="notice lock-notice">
        <strong>法律维护已锁定：</strong>{{ lockReason }}
      </p>
      <p v-else-if="detail.pendingRevision" class="notice">
        该法律已有正式标注版本，当前正文变更已记录为待修订。
      </p>

      <div class="detail-summary">
        <span class="status-pill primary">{{ displayStatus }}</span>
        <span class="status-pill">{{ validityLabels[detail.validityStatus] }}</span>
        <span class="status-pill">C{{ detail.currentContentVersionSeq }}</span>
        <span class="summary-time">更新于 {{ formatDateTimeToMinute(detail.updatedAt) }}</span>
      </div>

      <section class="card" aria-labelledby="law-base-heading">
        <div class="row-head detail-section-heading">
          <div>
            <h2 id="law-base-heading">基础信息</h2>
            <p class="muted">基础信息修改只写审计，不生成新的正文版本。</p>
          </div>
          <button
            type="button"
            :disabled="maintenanceDisabled"
            :title="lockReason"
            @click="run('base', () => updateLawBase(lawId, base))"
          >
            {{ saving === 'base' ? '保存中…' : '保存基础信息' }}
          </button>
        </div>
        <div class="form-grid">
          <label class="field">
            <span>法律名称</span>
            <input v-model="base.name" maxlength="100" :disabled="maintenanceDisabled" />
          </label>
          <label class="field">
            <span>发布机关</span>
            <input v-model="base.issuingAuthority" maxlength="100" :disabled="maintenanceDisabled" />
          </label>
          <label class="field">
            <span>发布日期</span>
            <input v-model="base.publicationDate" type="date" :disabled="maintenanceDisabled" />
          </label>
          <label class="field">
            <span>效力状态</span>
            <select v-model="base.validityStatus" :disabled="maintenanceDisabled">
              <option value="ACTIVE">现行有效</option>
              <option value="NOT_EFFECTIVE">尚未生效</option>
              <option value="INVALID">失效</option>
              <option value="REPEALED">已废止</option>
            </select>
          </label>
        </div>
      </section>

      <div class="detail-columns">
        <section class="card law-content-card" aria-labelledby="law-content-heading">
          <div class="row-head detail-section-heading">
            <div>
              <h2 id="law-content-heading">章节结构与法条</h2>
              <p class="muted">编、章、节仅用于组织和导航，不承载标注状态。</p>
            </div>
            <span class="badge">{{ displayedArticles.length }} / {{ detail.articles.length }} 条</span>
          </div>

          <div class="law-content-layout">
            <nav class="structure-tree" aria-label="法律章节树">
              <button
                type="button"
                class="tree-all"
                :class="{ selected: selectedNodeId === null }"
                @click="selectedNodeId = null"
              >
                全部法条
              </button>
              <p v-if="detail.structure.length === 0" class="tree-empty">暂无编、章、节结构</p>
              <div
                v-for="row in treeRows"
                :key="row.node.nodeId"
                class="tree-row"
                :style="{ paddingLeft: (row.depth * 18 + 8) + 'px' }"
              >
                <button
                  v-if="row.hasChildren"
                  type="button"
                  class="tree-toggle"
                  :aria-label="expandedNodeIds.has(row.node.nodeId) ? '折叠' : '展开'"
                  @click="toggleNode(row.node.nodeId)"
                >
                  {{ expandedNodeIds.has(row.node.nodeId) ? '▾' : '▸' }}
                </button>
                <span v-else class="tree-spacer"></span>
                <button
                  type="button"
                  class="tree-node"
                  :class="{ selected: selectedNodeId === row.node.nodeId }"
                  @click="selectedNodeId = row.node.nodeId"
                >
                  <small>{{ structureTypeLabels[row.node.type] }}</small>
                  <span>{{ row.node.title }}</span>
                </button>
              </div>
            </nav>

            <div class="article-list">
              <div class="article-list-heading">
                <strong>{{ selectedStructureTitle }}</strong>
                <span>{{ displayedArticles.length }} 条</span>
              </div>
              <div v-if="displayedArticles.length === 0" class="empty article-empty">
                该结构下暂无法条
              </div>
              <article
                v-for="article in displayedArticles"
                v-else
                :key="article.articleId"
                class="article-card"
              >
                <div class="article-heading">
                  <h3>{{ article.number }}</h3>
                  <span>{{ articlePath(article.articleId) }}</span>
                </div>
                <template v-if="articleEdit?.articleId === article.articleId">
                  <div class="row-grid article-editor">
                    <label class="field">
                      <span>条号</span>
                      <input v-model="articleEdit.number" maxlength="20" :disabled="mutationBusy" />
                    </label>
                    <label class="field">
                      <span>顺序</span>
                      <input v-model.number="articleEdit.order" min="0" max="2147483647" step="1" type="number" :disabled="mutationBusy" />
                    </label>
                    <label class="field full">
                      <span>正文</span>
                      <textarea v-model="articleEdit.body" rows="5" :disabled="mutationBusy"></textarea>
                    </label>
                  </div>
                  <div class="section-actions">
                    <button
                      class="small"
                      type="button"
                      :disabled="mutationBusy"
                      :title="lockReason"
                      @click="saveArticleEdit"
                    >
                      {{ saving === 'article-' + article.articleId ? '保存中…' : '保存法条' }}
                    </button>
                    <button
                      class="secondary small"
                      type="button"
                      :disabled="Boolean(saving)"
                      @click="cancelArticleEdit"
                    >
                      取消
                    </button>
                  </div>
                </template>
                <template v-else>
                  <p>{{ article.body }}</p>
                  <div class="section-actions">
                    <button
                      class="secondary small"
                      type="button"
                      :disabled="maintenanceDisabled"
                      :title="lockReason"
                      @click="startArticleEdit(article)"
                    >
                      编辑
                    </button>
                    <button
                      class="danger small"
                      type="button"
                      :disabled="maintenanceDisabled"
                      :title="lockReason"
                      @click="removeArticle(article.articleId)"
                    >
                      删除
                    </button>
                  </div>
                </template>
              </article>
            </div>
          </div>

          <details class="maintenance-panel">
            <summary>维护章节结构</summary>
            <p class="muted">结构调整只写审计，不生成新的正文版本。</p>
            <div class="section-actions">
              <button
                class="secondary small"
                type="button"
                :disabled="maintenanceDisabled"
                :title="lockReason"
                @click="addStructureNode"
              >
                新增结构
              </button>
            </div>
            <p v-if="structures.length === 0" class="muted">当前没有编、章、节结构。</p>
            <div v-for="node in structures" :key="node.nodeId" class="row-card">
              <div class="row-head">
                <strong>{{ node.title || '未命名结构' }}</strong>
                <button
                  class="danger small"
                  type="button"
                  :disabled="maintenanceDisabled"
                  :title="lockReason"
                  @click="removeStructureNode(node.nodeId)"
                >
                  移除
                </button>
              </div>
              <div class="row-grid">
                <label class="field">
                  <span>类型</span>
                  <select v-model="node.type" :disabled="maintenanceDisabled">
                    <option v-for="item in structureTypes" :key="item.value" :value="item.value">
                      {{ item.label }}
                    </option>
                  </select>
                </label>
                <label class="field">
                  <span>标题</span>
                  <input v-model="node.title" maxlength="100" :disabled="maintenanceDisabled" />
                </label>
                <label class="field">
                  <span>顺序</span>
                  <input v-model.number="node.order" min="0" max="2147483647" step="1" type="number" :disabled="maintenanceDisabled" />
                </label>
                <label class="field">
                  <span>上级结构</span>
                  <select v-model="node.parentNodeId" :disabled="maintenanceDisabled">
                    <option :value="null">无</option>
                    <option
                      v-for="parent in structures.filter((item) => item.nodeId !== node.nodeId)"
                      :key="parent.nodeId"
                      :value="parent.nodeId"
                    >
                      {{ parent.title || parent.nodeId }}
                    </option>
                  </select>
                </label>
                <label class="field full">
                  <span>包含法条（可多选）</span>
                  <select v-model="node.articleRefs" multiple size="5" :disabled="maintenanceDisabled">
                    <option
                      v-for="article in detail.articles"
                      :key="article.articleId"
                      :value="article.articleId"
                    >
                      {{ article.number }}
                    </option>
                  </select>
                </label>
              </div>
            </div>
            <div class="section-actions">
              <button
                type="button"
                :disabled="maintenanceDisabled"
                :title="lockReason"
                @click="run('structure', () => updateLawStructure(lawId, structures))"
              >
                {{ saving === 'structure' ? '保存中…' : '保存结构' }}
              </button>
            </div>
          </details>

          <details class="maintenance-panel">
            <summary>新增法条</summary>
            <p class="muted">新增法条会创建新的不可变正文版本。</p>
            <div class="row-grid">
              <label class="field">
                <span>条号</span>
                <input v-model="newArticle.number" maxlength="20" :disabled="maintenanceDisabled" />
              </label>
              <label class="field">
                <span>顺序</span>
                <input v-model.number="newArticle.order" min="0" max="2147483647" step="1" type="number" :disabled="maintenanceDisabled" />
              </label>
              <label class="field full">
                <span>正文</span>
                <textarea v-model="newArticle.body" rows="5" :disabled="maintenanceDisabled"></textarea>
              </label>
            </div>
            <div class="section-actions">
              <button
                type="button"
                :disabled="maintenanceDisabled || !newArticle.number.trim() || !newArticle.body.trim()"
                :title="lockReason"
                @click="createArticle"
              >
                {{ saving === 'new-article' ? '添加中…' : '添加法条' }}
              </button>
            </div>
          </details>
        </section>

        <aside class="detail-sidebar" aria-label="法律当前状态">
          <section class="card status-card">
            <h2>当前状态</h2>
            <dl class="status-list">
              <div><dt>标注状态</dt><dd><span class="status-pill primary">{{ displayStatus }}</span></dd></div>
              <div><dt>未结束任务</dt><dd>{{ hasActiveTask ? '存在' : '不存在' }}</dd></div>
              <div><dt>修订状态</dt><dd>{{ detail.pendingRevision ? '待修订' : '无需修订' }}</dd></div>
            </dl>

            <div v-if="currentTask" class="status-block">
              <h3>当前任务</h3>
              <p><strong>{{ currentTask.taskName || currentTask.taskId }}</strong></p>
              <p>{{ TASK_TYPE_LABELS[currentTask.taskType] }} · {{ TASK_STATE_LABELS[currentTask.taskState] }}</p>
              <p>标注员：{{ currentTask.annotatorName }}</p>
              <RouterLink
                class="inline-link"
                :to="{ name: 'admin-task-detail', params: { taskId: currentTask.taskId } }"
              >
                查看任务
              </RouterLink>
            </div>
            <div v-else-if="hasActiveTask" class="status-block">
              <h3>当前任务</h3>
              <p class="muted">后端状态显示存在未结束任务，当前任务资料未由普通任务接口返回。</p>
            </div>
            <div v-else class="status-block">
              <h3>当前任务</h3>
              <p class="muted">当前没有未结束任务</p>
            </div>

            <div class="status-block">
              <h3>正式标注结果</h3>
              <p v-if="latestApprovedTask">
                最近通过任务：{{ latestApprovedTask.taskName || latestApprovedTask.taskId }}
              </p>
              <p class="muted">正式版本详情接口由后续审核结果 PR 提供。</p>
              <button class="secondary small" type="button" disabled>查看正式结果（后续 PR）</button>
            </div>

            <div class="status-block">
              <h3>当前正文版本</h3>
              <p><strong>C{{ detail.currentContentVersionSeq }}</strong></p>
              <p class="version-id">{{ detail.currentContentVersionId }}</p>
            </div>
          </section>

          <section class="card reserved-card">
            <h2>相关功能</h2>
            <p class="muted">保留入口，不提前实现后续 PR 的历史详情和导出。</p>
            <button type="button" disabled>历史记录（后续 PR）</button>
            <button type="button" disabled>导出（后续 PR）</button>
          </section>
        </aside>
      </div>
    </template>
  </section>
</template>

<style src="./law.css"></style>
