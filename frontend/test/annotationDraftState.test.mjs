import assert from 'node:assert/strict'
import test from 'node:test'

import {
  annotationClearPresentation,
  annotationSubmissionAction,
  articleCompletionForTask,
  canEditAnnotationTarget,
  createArticleForm,
  createOverallForm,
  decideAnnotationNavigation,
  formsEqual,
  isArticleDraftComplete,
  parseAnnotationLocator,
  reconcileSavedForm,
  revisionTargetStatus,
  reviewIssueTarget,
  sameWorkbenchSession,
  selectInitialTarget,
} from '../src/views/annotation/annotationDraftState.ts'

const task = {
  taskType: 'ORDINARY',
  taskState: 'ANNOTATING',
  contentVersionSnapshot: {
    articles: [
      { articleId: 'a-1', number: '第一条', body: '正文一', order: 1 },
      { articleId: 'a-2', number: '第二条', body: '正文二', order: 2 },
    ],
  },
  fieldConfigSnapshot: {
    overall: [
      { fieldKey: 'lawCategory', required: true },
      { fieldKey: 'overallKeywords', required: true },
      { fieldKey: 'summary', required: false },
    ],
    article: [
      { fieldKey: 'itemType', required: true },
      { fieldKey: 'keywords', required: true },
      { fieldKey: 'subjects', required: false },
    ],
  },
}

function draft(overrides = {}) {
  return {
    overallDraft: null,
    articleDrafts: {},
    progress: { totalArticles: 2, filledArticles: 0, overallCompleted: false },
    revision: 0,
    updatedAt: null,
    editableScope: { overallEditable: true, editableArticleIds: ['a-1', 'a-2'] },
    reviewIssues: [],
    ...overrides,
  }
}

test('首次进入无服务器草稿的标注中任务默认定位整体信息', () => {
  assert.deepEqual(selectInitialTarget(task, draft()), { kind: 'overall' })
})

test('重新进入已有草稿的标注中任务定位第一条未完成法条', () => {
  const saved = draft({
    articleDrafts: {
      'a-1': { itemType: 'DEFINITION', keywords: '定义', subjects: null, legalLiability: null, annotationNote: null },
    },
    progress: { totalArticles: 2, filledArticles: 1, overallCompleted: false },
    revision: 1,
    updatedAt: '2026-08-25T00:00:00Z',
  })

  assert.deepEqual(selectInitialTarget(task, saved), { kind: 'article', articleId: 'a-2' })
})

test('所有法条完成时默认回到整体信息', () => {
  const completedArticle = { itemType: 'OTHER', keywords: '关键词', subjects: null, legalLiability: null, annotationNote: null }
  const saved = draft({
    articleDrafts: { 'a-1': completedArticle, 'a-2': completedArticle },
    progress: { totalArticles: 2, filledArticles: 2, overallCompleted: false },
    revision: 3,
    updatedAt: '2026-08-25T00:00:00Z',
  })

  assert.deepEqual(selectInitialTarget(task, saved), { kind: 'overall' })
  assert.deepEqual(selectInitialTarget(task, { ...saved, progress: { ...saved.progress, overallCompleted: true } }), { kind: 'overall' })
})

test('单条完成状态只读取服务器已保存草稿和字段快照', () => {
  assert.equal(isArticleDraftComplete(task.fieldConfigSnapshot.article, null), false)
  assert.equal(isArticleDraftComplete(task.fieldConfigSnapshot.article, {
    itemType: 'DEFINITION', keywords: '已保存', subjects: null, legalLiability: null, annotationNote: null,
  }), true)
  assert.equal(isArticleDraftComplete(task.fieldConfigSnapshot.article, {
    itemType: 'DEFINITION', keywords: null, subjects: '当前未保存表单不应被读取', legalLiability: null, annotationNote: null,
  }), false)
})

test('普通任务继续按完整字段显示已完成，修订任务不把合并的基础值当作本轮完成', () => {
  const completeBaseValue = {
    itemType: 'DEFINITION', keywords: '基础标注', subjects: null,
    legalLiability: null, annotationNote: null,
  }
  assert.equal(isArticleDraftComplete(task.fieldConfigSnapshot.article, completeBaseValue), true)
  assert.equal(articleCompletionForTask('ORDINARY', task.fieldConfigSnapshot.article, completeBaseValue), true)
  assert.equal(articleCompletionForTask('REVISION', task.fieldConfigSnapshot.article, completeBaseValue), null)

  const revision = {
    ...task,
    taskType: 'REVISION',
    revisionScope: {
      mode: 'ANNOTATION_ONLY', overall: true,
      articleIds: ['a-1'], mandatoryArticleIds: [],
    },
  }
  assert.equal(revisionTargetStatus(
    revision,
    { kind: 'article', articleId: 'a-1' },
    draft({ articleDrafts: { 'a-1': completeBaseValue } }),
  ), '当前可修改')
})

test('修订单项状态只表达 editableScope、原范围和 mandatory 角色', () => {
  const revision = {
    ...task,
    taskType: 'REVISION',
    revisionScope: {
      mode: 'CONTENT_CHANGE', overall: true,
      articleIds: ['a-1', 'a-2'], mandatoryArticleIds: ['a-1'],
    },
  }
  const editable = draft({
    editableScope: { overallEditable: true, editableArticleIds: ['a-1', 'a-2'] },
  })
  assert.equal(revisionTargetStatus(revision, { kind: 'overall' }, editable), '当前可修改')
  assert.equal(revisionTargetStatus(revision, { kind: 'article', articleId: 'a-1' }, editable), '必修订·当前可修改')
  assert.equal(revisionTargetStatus(revision, { kind: 'article', articleId: 'a-2' }, editable), '当前可修改')

  const readonly = draft({ editableScope: { overallEditable: false, editableArticleIds: [] } })
  assert.equal(revisionTargetStatus(revision, { kind: 'overall' }, readonly), '原修订范围·只读')
  assert.equal(revisionTargetStatus(revision, { kind: 'article', articleId: 'a-1' }, readonly), '正文变化范围·只读')
  assert.equal(revisionTargetStatus(revision, { kind: 'article', articleId: 'a-2' }, readonly), '原修订范围·只读')
  assert.equal(revisionTargetStatus(revision, { kind: 'article', articleId: 'outside' }, readonly), '范围外·只读')
  assert.equal(revisionTargetStatus(task, { kind: 'article', articleId: 'a-1' }, editable), null)
})

test('清除操作为普通任务保留清空语义，为修订任务表达撤销并恢复服务器基准', () => {
  assert.deepEqual(annotationClearPresentation('ORDINARY', '第一条'), {
    actionLabel: '清空当前标注',
    title: '确认清空当前标注',
    description: '确定清空“第一条”已保存的标注吗？该区域会恢复为未完成状态。',
    confirmLabel: '确认清空',
    successMessage: '第一条标注已清空',
    errorFallback: '清空标注失败，请稍后重试',
  })
  assert.deepEqual(annotationClearPresentation('REVISION', '整体信息'), {
    actionLabel: '撤销本次修订',
    title: '确认撤销本次修订',
    description: '将撤销“整体信息”本轮已保存的修订内容，并恢复服务器提供的修订基准内容。对于新增法条，撤销后标注内容可能为空。如果该项仍属于当前可编辑范围，需要重新保存后才能提交审核。',
    confirmLabel: '确认撤销',
    successMessage: '整体信息本次修订已撤销，已恢复服务器基准内容',
    errorFallback: '撤销本次修订失败，请稍后重试',
  })
})

test('表单副本可准确识别真实差异并不修改服务器值', () => {
  const savedOverall = { lawCategory: '民事', overallKeywords: '合同', summary: null, overallNote: null }
  const baseline = createOverallForm(savedOverall)
  const current = createOverallForm(savedOverall)
  current.summary = '未保存摘要'

  assert.equal(formsEqual(baseline, current), false)
  assert.equal(savedOverall.summary, null)
  assert.deepEqual(createArticleForm(null), {
    itemType: '', keywords: '', subjects: '', legalLiability: '', annotationNote: '',
  })
})

test('422 locator 可解析为整体或具体法条目标', () => {
  assert.deepEqual(parseAnnotationLocator('overall.summary'), {
    target: { kind: 'overall' }, fieldKey: 'summary',
  })
  assert.deepEqual(parseAnnotationLocator('articles.a-2.keywords'), {
    target: { kind: 'article', articleId: 'a-2' }, fieldKey: 'keywords',
  })
  assert.deepEqual(parseAnnotationLocator('overall'), {
    target: { kind: 'overall' }, fieldKey: '',
  })
  assert.deepEqual(parseAnnotationLocator('articles.a-2'), {
    target: { kind: 'article', articleId: 'a-2' }, fieldKey: '',
  })
  assert.equal(parseAnnotationLocator('unknown.path'), null)
})

test('reviewIssues locator 可定位整体或真实法条', () => {
  assert.deepEqual(reviewIssueTarget({ type: 'OVERALL', articleId: null }), { kind: 'overall' })
  assert.deepEqual(reviewIssueTarget({ type: 'ARTICLE', articleId: 'a-2' }), {
    kind: 'article', articleId: 'a-2',
  })
  assert.equal(reviewIssueTarget({ type: 'ARTICLE', articleId: null }), null)
})

test('普通任务继续沿用已有草稿初始定位规则', () => {
  const ordinary = { ...task, taskType: 'ORDINARY' }
  assert.deepEqual(selectInitialTarget(ordinary, draft()), { kind: 'overall' })
})

test('修订任务初始位置只读取 editableScope 并按目录 DFS 选择首个可编辑法条', () => {
  const revision = {
    ...task,
    taskType: 'REVISION',
    taskState: 'PARTIALLY_REJECTED',
    structureSnapshot: [
      { nodeId: 'chapter-2', type: 'CHAPTER', title: '第二章', parentNodeId: null, order: 2, articleIds: ['a-2'] },
      { nodeId: 'chapter-1', type: 'CHAPTER', title: '第一章', parentNodeId: null, order: 1, articleIds: ['a-1'] },
    ],
    revisionScope: { mode: 'ANNOTATION_ONLY', overall: true, articleIds: ['a-1', 'a-2'], mandatoryArticleIds: [] },
  }
  const response = draft({ editableScope: { overallEditable: false, editableArticleIds: ['a-2'] } })
  assert.deepEqual(selectInitialTarget(revision, response), { kind: 'article', articleId: 'a-2' })
  assert.deepEqual(selectInitialTarget(revision, {
    ...response, editableScope: { overallEditable: false, editableArticleIds: ['a-1', 'a-2'] },
  }), { kind: 'article', articleId: 'a-1' })
  assert.deepEqual(selectInitialTarget(revision, {
    ...response, editableScope: { overallEditable: true, editableArticleIds: ['a-2'] },
  }), { kind: 'overall' })
  assert.deepEqual(selectInitialTarget(revision, {
    ...response, editableScope: { overallEditable: false, editableArticleIds: [] },
  }), { kind: 'overall' })
})

test('修订编辑权限和提交动作严格按 taskType、taskState 与 editableScope 决定', () => {
  const articleOnly = {
    ...task,
    taskType: 'REVISION',
    taskState: 'ANNOTATING',
    revisionScope: { mode: 'ANNOTATION_ONLY', overall: false, articleIds: ['a-1'], mandatoryArticleIds: [] },
  }
  const articleDraft = draft({ editableScope: { overallEditable: false, editableArticleIds: ['a-1'] } })
  assert.equal(canEditAnnotationTarget(articleOnly, { kind: 'overall' }, articleDraft), false)
  assert.equal(canEditAnnotationTarget(articleOnly, { kind: 'article', articleId: 'a-1' }, articleDraft), true)
  assert.equal(canEditAnnotationTarget(articleOnly, { kind: 'article', articleId: 'a-2' }, articleDraft), false)
  assert.equal(annotationSubmissionAction(articleOnly, articleDraft), 'review')

  const deletionOnly = {
    ...articleOnly,
    revisionScope: { mode: 'CONTENT_CHANGE', overall: false, articleIds: [], mandatoryArticleIds: [] },
  }
  assert.equal(annotationSubmissionAction(deletionOnly, draft({
    editableScope: { overallEditable: false, editableArticleIds: [] },
  })), 'review')

  const rejected = { ...articleOnly, taskState: 'PARTIALLY_REJECTED' }
  assert.equal(annotationSubmissionAction(rejected, articleDraft), 'rereview')
  assert.equal(canEditAnnotationTarget({ ...task, taskState: 'PARTIALLY_REJECTED' }, { kind: 'overall' }, articleDraft), false)
})

test('未保存状态下切换任务路由必须先确认', () => {
  assert.equal(decideAnnotationNavigation(
    { kind: 'overall' },
    true,
    { kind: 'route', path: '/my-tasks/task-2/annotation' },
  ), 'confirm')
})

test('422 定位切换到其他目标时复用未保存确认，当前目标仍可直接聚焦', () => {
  assert.equal(decideAnnotationNavigation(
    { kind: 'overall' },
    true,
    { kind: 'target', target: { kind: 'article', articleId: 'a-2' }, focusFieldKey: 'keywords' },
  ), 'confirm')
  assert.equal(decideAnnotationNavigation(
    { kind: 'overall' },
    true,
    { kind: 'target', target: { kind: 'overall' }, focusFieldKey: 'summary' },
  ), 'apply')
})

test('同一 taskId 的旧访问代次不能回写当前工作台', () => {
  assert.equal(sameWorkbenchSession(
    { taskId: 'task-a', generation: 1 },
    { taskId: 'task-a', generation: 3 },
  ), false)
  assert.equal(sameWorkbenchSession(
    { taskId: 'task-a', generation: 3 },
    { taskId: 'task-a', generation: 3 },
  ), true)
})

test('保存响应只更新 baseline，不覆盖请求发出后的新输入', () => {
  const submitted = { summary: '提交时内容' }
  const serverSaved = { summary: '提交时内容' }
  assert.deepEqual(reconcileSavedForm(serverSaved, submitted, { summary: '保存期间新增输入' }), {
    baseline: serverSaved,
    current: { summary: '保存期间新增输入' },
    changedAfterRequest: true,
  })
  assert.deepEqual(reconcileSavedForm(serverSaved, submitted, submitted), {
    baseline: serverSaved,
    current: serverSaved,
    changedAfterRequest: false,
  })
})
