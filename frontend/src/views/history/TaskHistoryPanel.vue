<script setup lang="ts">
import { computed } from 'vue'

import type { ReviewItemLocator } from '../../types/review'
import type { TaskHistory } from '../../types/history'
import { formatTaskDateTime, TASK_STATE_LABELS, TASK_TYPE_LABELS } from '../../types/task'
import TaskStatusBadge from '../task/TaskStatusBadge.vue'
import HistoryAnnotationValues from './HistoryAnnotationValues.vue'
import { annotationArticleRows, historyFieldLabel, validityStatusLabel } from './historyPresentation'

const props = defineProps<{ detail: TaskHistory }>()

const articlesById = computed(() => new Map(
  props.detail.contentVersionSnapshot.articles.map((article) => [article.articleId, article]),
))

const structureTypeLabels = { PART: '编', CHAPTER: '章', SECTION: '节' } as const
const reviewTypeLabels = { INITIAL_REVIEW: '初审', REREVIEW: '复审' } as const
const reviewStateLabels = { UNREVIEWED: '未审核', CHECKED: '已核查', NEEDS_CHANGE: '待修改' } as const

function scopeLabel(locator: ReviewItemLocator): string {
  if (locator.type === 'OVERALL') return '整体信息'
  if (!locator.articleId) return '未知法条'
  return articlesById.value.get(locator.articleId)?.number ?? `法条 ${locator.articleId}`
}

function revisionArticleLabel(articleId: string): string {
  return articlesById.value.get(articleId)?.number ?? articleId
}
</script>

<template>
  <div class="history-detail-stack">
    <section class="panel history-detail-card">
      <div class="history-detail-heading">
        <div><span class="history-kicker">任务完整历史快照</span><div class="history-title-with-status"><h1>{{ detail.taskName }}</h1><TaskStatusBadge :state="detail.taskState" /></div></div>
        <span class="history-readonly-badge">只读</span>
      </div>
      <p v-if="detail.lawDeleted" class="history-warning">关联法律已进入回收站<span v-if="detail.lawDeletedAt">，删除时间：{{ formatTaskDateTime(detail.lawDeletedAt) }}</span>。以下内容来自任务创建时快照。</p>
      <dl class="history-definition-grid"><div><dt>任务类型</dt><dd>{{ TASK_TYPE_LABELS[detail.taskType] }}</dd></div><div><dt>任务状态</dt><dd>{{ TASK_STATE_LABELS[detail.taskState] }}</dd></div><div><dt>标注员快照</dt><dd>{{ detail.annotatorNameSnapshot }}（{{ detail.annotatorId }}）</dd></div><div><dt>创建人 ID</dt><dd>{{ detail.createdBy }}</dd></div><div><dt>创建时间</dt><dd>{{ formatTaskDateTime(detail.createdAt) }}</dd></div><div><dt>更新时间</dt><dd>{{ formatTaskDateTime(detail.updatedAt) }}</dd></div><div><dt>任务 ID</dt><dd>{{ detail.taskId }}</dd></div><div><dt>法律 ID</dt><dd>{{ detail.lawId }}</dd></div><div><dt>绑定内容版本</dt><dd>C{{ detail.contentVersionSnapshot.seq }}（{{ detail.contentVersionId }}）</dd></div><div><dt>首次提交 ID</dt><dd>{{ detail.initialSubmissionId || '—' }}</dd></div><div><dt>当前提交 ID</dt><dd>{{ detail.currentSubmissionId || '—' }}</dd></div><div><dt>当前审核轮次 ID</dt><dd>{{ detail.currentReviewRoundId || '—' }}</dd></div></dl>
      <div class="history-remark"><strong>任务备注</strong><p>{{ detail.remark || '无' }}</p></div>
    </section>

    <section class="panel history-detail-card"><h2>法律基础信息快照</h2><dl class="history-definition-grid"><div><dt>法律名称</dt><dd>{{ detail.lawBaseInfoSnapshot.name }}</dd></div><div><dt>发布机关</dt><dd>{{ detail.lawBaseInfoSnapshot.issuingAuthority }}</dd></div><div><dt>发布日期</dt><dd>{{ detail.lawBaseInfoSnapshot.publicationDate }}</dd></div><div><dt>效力状态</dt><dd>{{ validityStatusLabel(detail.lawBaseInfoSnapshot.validityStatus) }}</dd></div></dl></section>

    <section v-if="detail.taskType === 'REVISION'" class="panel history-detail-card"><h2>修订范围快照</h2><template v-if="detail.revisionScope"><dl class="history-definition-grid"><div><dt>修订模式</dt><dd>{{ detail.revisionScope.mode === 'CONTENT_CHANGE' ? '正文变化型' : '标注修正型' }}</dd></div><div><dt>整体信息</dt><dd>{{ detail.revisionScope.overall ? '包含' : '不包含' }}</dd></div><div><dt>基础标注版本 ID</dt><dd>{{ detail.baseAnnotationVersionId || '—' }}</dd></div><div><dt>批准标注版本 ID</dt><dd>{{ detail.approvedAnnotationVersionId || '—' }}</dd></div></dl><div class="history-chip-list"><span v-for="articleId in detail.revisionScope.articleIds" :key="articleId">{{ revisionArticleLabel(articleId) }}<small v-if="detail.revisionScope.mandatoryArticleIds.includes(articleId)">必须处理</small></span></div><p v-if="detail.revisionScope.articleIds.length === 0" class="history-empty-inline">修订范围不包含法条</p></template><p v-else class="history-empty-inline">任务快照未包含修订范围</p></section>

    <section v-if="detail.taskState === 'CANCELED'" class="panel history-detail-card"><h2>取消信息</h2><div class="history-cancel-box"><p>{{ detail.cancelReason || '未提供取消原因' }}</p><dl class="history-definition-grid"><div><dt>取消人 ID</dt><dd>{{ detail.canceledBy || '—' }}</dd></div><div><dt>取消时间</dt><dd>{{ formatTaskDateTime(detail.canceledAt) }}</dd></div></dl></div></section>

    <section class="panel history-detail-card"><h2>法律结构快照</h2><p v-if="detail.structureSnapshot.length === 0" class="history-empty-inline">该任务没有编、章、节结构快照</p><div v-else class="history-table-scroll"><table class="history-table"><thead><tr><th>类型</th><th>标题</th><th>上级节点</th><th>顺序</th><th>包含法条</th></tr></thead><tbody><tr v-for="node in detail.structureSnapshot" :key="node.nodeId"><td>{{ structureTypeLabels[node.type] }}</td><td>{{ node.title }}</td><td>{{ node.parentNodeId || '—' }}</td><td>{{ node.order }}</td><td>{{ node.articleIds.map(revisionArticleLabel).join('、') || '—' }}</td></tr></tbody></table></div></section>

    <section class="panel history-detail-card"><h2>内容版本快照</h2><div class="history-article-list"><details v-for="article in detail.contentVersionSnapshot.articles" :key="article.articleId"><summary><strong>{{ article.number }}</strong><span>顺序 {{ article.order }}</span></summary><p>{{ article.body }}</p><small>法条 ID：{{ article.articleId }}</small></details></div></section>

    <section class="panel history-detail-card"><h2>字段配置快照</h2><div class="history-field-config-grid"><div><h3>整体字段</h3><ul><li v-for="field in detail.fieldConfigSnapshot.overall" :key="field.fieldKey"><span>{{ historyFieldLabel('overall', field.fieldKey) }}</span><strong>{{ field.required ? '必填' : '选填' }}</strong></li></ul></div><div><h3>法条字段</h3><ul><li v-for="field in detail.fieldConfigSnapshot.article" :key="field.fieldKey"><span>{{ historyFieldLabel('article', field.fieldKey) }}</span><strong>{{ field.required ? '必填' : '选填' }}</strong></li></ul></div></div></section>

    <section class="panel history-detail-card"><h2>提交快照</h2><p v-if="detail.submissions.length === 0" class="history-empty-inline">该任务没有提交记录</p><div v-else class="history-record-list"><details v-for="submission in detail.submissions" :key="submission.submissionId"><summary><strong>第 {{ submission.submissionNo }} 次提交</strong><span>{{ formatTaskDateTime(submission.submittedAt) }}</span></summary><dl class="history-definition-grid"><div><dt>提交 ID</dt><dd>{{ submission.submissionId }}</dd></div><div><dt>提交人 ID</dt><dd>{{ submission.submittedBy }}</dd></div><div><dt>草稿修订号</dt><dd>{{ submission.draftRevision }}</dd></div><div><dt>来源审核轮次</dt><dd>{{ submission.sourceReviewRoundId || '—' }}</dd></div></dl><h3>整体标注</h3><HistoryAnnotationValues :overall="submission.overallSnapshot" /><h3>法条标注</h3><div class="history-submission-articles"><details v-for="row in annotationArticleRows(submission.articleSnapshots, detail.contentVersionSnapshot.articles)" :key="row.articleId"><summary>{{ row.number }}</summary><div class="history-article-body">{{ row.body }}</div><HistoryAnnotationValues :article="row.values" /></details></div><div v-if="submission.modifiedScope.length" class="history-scope-line"><strong>本次修改范围：</strong>{{ submission.modifiedScope.map(scopeLabel).join('、') }}</div></details></div></section>

    <section class="panel history-detail-card"><h2>审核轮次快照</h2><p v-if="detail.reviewRounds.length === 0" class="history-empty-inline">该任务没有审核轮次</p><div v-else class="history-record-list"><details v-for="round in detail.reviewRounds" :key="round.reviewRoundId"><summary><strong>第 {{ round.roundNo }} 轮{{ reviewTypeLabels[round.roundType] }}</strong><span>{{ round.completedAt ? `完成于 ${formatTaskDateTime(round.completedAt)}` : round.startedAt ? `开始于 ${formatTaskDateTime(round.startedAt)}` : '尚未开始' }}</span></summary><dl class="history-definition-grid"><div><dt>审核轮次 ID</dt><dd>{{ round.reviewRoundId }}</dd></div><div><dt>审核人 ID</dt><dd>{{ round.reviewerId || '—' }}</dd></div><div><dt>来源提交 ID</dt><dd>{{ round.sourceSubmissionId }}</dd></div><div><dt>上一提交 ID</dt><dd>{{ round.previousSubmissionId || '—' }}</dd></div><div><dt>创建时间</dt><dd>{{ formatTaskDateTime(round.createdAt) }}</dd></div><div><dt>完成准备时间</dt><dd>{{ formatTaskDateTime(round.completionStartedAt) }}</dd></div><div><dt>完成结论</dt><dd>{{ round.completionOutcome === 'APPROVED' ? '审核通过' : round.completionOutcome === 'PARTIALLY_REJECTED' ? '部分驳回' : '未完成' }}</dd></div><div><dt>形成标注版本 ID</dt><dd>{{ round.annotationVersionId || '—' }}</dd></div><div><dt>已审核 / 总数</dt><dd>{{ round.reviewedCount }} / {{ round.totalCount }}</dd></div><div><dt>未审核项</dt><dd>{{ round.unreviewedCount }}</dd></div><div><dt>待修改项</dt><dd>{{ round.needsChangeCount }}</dd></div></dl><div class="history-scope-line"><strong>必审范围：</strong>{{ round.requiredScope.map(scopeLabel).join('、') || '空' }}</div><div v-if="round.itemStates.length" class="history-table-scroll"><table class="history-table"><thead><tr><th>审核项</th><th>状态</th></tr></thead><tbody><tr v-for="item in round.itemStates" :key="`${item.locator.type}-${item.locator.articleId || 'overall'}`"><td>{{ scopeLabel(item.locator) }}</td><td>{{ reviewStateLabels[item.state] }}</td></tr></tbody></table></div><div class="history-issues"><h3>问题项</h3><p v-if="round.issues.length === 0" class="history-empty-inline">本轮没有问题项</p><article v-for="issue in round.issues" :key="`${issue.locator.type}-${issue.locator.articleId || 'overall'}-${issue.createdAt}`"><strong>{{ scopeLabel(issue.locator) }}</strong><p>{{ issue.reason }}</p><small>提出人 ID：{{ issue.actorId }} · {{ formatTaskDateTime(issue.createdAt) }}</small></article></div></details></div></section>
  </div>
</template>
