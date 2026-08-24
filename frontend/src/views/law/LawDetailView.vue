<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { apiErrorMessage, getLaw } from '../../api/laws'
import type {
  LawDetailView,
  LawStructureNode,
  LawTaskState,
  StructureNodeType,
  ValidityStatus,
} from '../../types/law'

interface TreeRow {
  node: LawStructureNode
  depth: number
  hasChildren: boolean
}

const route = useRoute()
const lawId = String(route.params.lawId)
const detail = ref<LawDetailView | null>(null)
const loading = ref(true)
const error = ref('')
const expandedNodeIds = ref<Set<string>>(new Set())
const selectedNodeId = ref<string | null>(null)

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

const taskStateLabels: Record<LawTaskState, string> = {
  PENDING_ANNOTATION: '待标注',
  ANNOTATING: '标注中',
  PENDING_REVIEW: '待审核',
  PARTIALLY_REJECTED: '部分驳回',
  PENDING_REREVIEW: '待复审',
  APPROVED: '已通过',
  CANCELED: '已取消',
}

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
    const node = nodeById.value.get(nodeId)
    node?.articleIds.forEach((articleId) => articleIds.add(articleId))
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
  selectedNodeId.value ? nodeById.value.get(selectedNodeId.value)?.title ?? '所选结构' : '全部法条'
))

const displayStatus = computed(() => {
  if (!detail.value) return ''
  if (detail.value.currentTask?.taskType === 'REVISION') return '修订中'
  if (detail.value.pendingRevision) return '待修订'
  if (detail.value.currentTask) return taskStateLabels[detail.value.currentTask.taskState]
  return detail.value.currentAnnotationVersion ? '已完成' : '未标注'
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await getLaw(lawId)
    detail.value = response
    expandedNodeIds.value = new Set(response.structure.map((node) => node.nodeId))
  } catch (caught) {
    error.value = apiErrorMessage(caught)
  } finally {
    loading.value = false
  }
}

function toggleNode(nodeId: string) {
  const next = new Set(expandedNodeIds.value)
  if (next.has(nodeId)) next.delete(nodeId)
  else next.add(nodeId)
  expandedNodeIds.value = next
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}

onMounted(load)
</script>

<template>
  <section class="law-page law-detail-page">
    <div class="page-title">
      <div>
        <p class="eyebrow">法律条文管理 / 法律详情</p>
        <h1>{{ detail?.name || '法律详情' }}</h1>
        <p class="muted">查看当前有效基础信息、章节结构、法条内容与业务状态</p>
      </div>
      <RouterLink class="button secondary" :to="{ name: 'law-list' }">返回法律列表</RouterLink>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <div v-if="loading" class="card empty">正在加载法律详情…</div>

    <template v-else-if="detail">
      <div class="detail-summary">
        <span class="status-pill primary">{{ displayStatus }}</span>
        <span class="status-pill">{{ validityLabels[detail.validityStatus] }}</span>
        <span class="status-pill">C{{ detail.currentContentVersion.seq }}</span>
        <span class="summary-time">更新于 {{ formatDateTime(detail.updatedAt) }}</span>
      </div>

      <section class="card" aria-labelledby="law-base-heading">
        <h2 id="law-base-heading">基础信息</h2>
        <dl class="definition-grid">
          <div><dt>法律名称</dt><dd>{{ detail.name }}</dd></div>
          <div><dt>发布机关</dt><dd>{{ detail.issuingAuthority }}</dd></div>
          <div><dt>发布日期</dt><dd>{{ detail.publicationDate }}</dd></div>
          <div><dt>效力状态</dt><dd>{{ validityLabels[detail.validityStatus] }}</dd></div>
          <div><dt>法条数量</dt><dd>{{ detail.articles.length }} 条</dd></div>
          <div><dt>更新时间</dt><dd>{{ formatDateTime(detail.updatedAt) }}</dd></div>
        </dl>
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
                :style="{ paddingLeft: `${row.depth * 18 + 8}px` }"
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
              <div v-if="displayedArticles.length === 0" class="empty article-empty">该结构下暂无法条</div>
              <template v-else>
                <article
                  v-for="article in displayedArticles"
                  :key="article.articleId"
                  class="article-card"
                >
                  <div class="article-heading">
                    <h3>{{ article.number }}</h3>
                    <span>{{ article.chapterPath.length ? article.chapterPath.join(' / ') : '未归入章节' }}</span>
                  </div>
                  <p>{{ article.body }}</p>
                </article>
              </template>
            </div>
          </div>
        </section>

        <aside class="detail-sidebar" aria-label="法律当前状态">
          <section class="card status-card">
            <h2>当前状态</h2>
            <dl class="status-list">
              <div><dt>法律状态</dt><dd><span class="status-pill primary">{{ displayStatus }}</span></dd></div>
              <div><dt>未结束任务</dt><dd>{{ detail.hasActiveTask ? '存在' : '不存在' }}</dd></div>
              <div><dt>修订状态</dt><dd>{{ detail.pendingRevision ? '待修订' : '无需修订' }}</dd></div>
            </dl>

            <div v-if="detail.currentTask" class="status-block">
              <h3>当前任务</h3>
              <p><strong>{{ detail.currentTask.taskName || detail.currentTask.taskId }}</strong></p>
              <p>{{ detail.currentTask.taskType === 'REVISION' ? '修订任务' : '普通任务' }} · {{ taskStateLabels[detail.currentTask.taskState] }}</p>
              <p>标注员：{{ detail.currentTask.annotatorName }}</p>
            </div>

            <div class="status-block">
              <h3>正式标注版本</h3>
              <p v-if="detail.currentAnnotationVersion">
                {{ detail.currentAnnotationVersion.id }}
              </p>
              <p v-else class="muted">尚无正式标注版本</p>
            </div>

            <div class="status-block">
              <h3>当前正文版本</h3>
              <p><strong>C{{ detail.currentContentVersion.seq }}</strong></p>
              <p class="version-id">{{ detail.currentContentVersion.id }}</p>
              <p>生成于 {{ formatDateTime(detail.currentContentVersion.createdAt) }}</p>
            </div>
          </section>

          <section class="card reserved-card">
            <h2>后续功能入口</h2>
            <p class="muted">当前仅展示入口状态，本 PR 不提供历史详情或导出操作。</p>
            <button type="button" disabled>
              历史记录{{ detail.hasHistory ? '（存在记录）' : '（暂无记录）' }}
            </button>
            <button type="button" disabled>导出</button>
          </section>
        </aside>
      </div>
    </template>
  </section>
</template>

<style src="./law.css"></style>
