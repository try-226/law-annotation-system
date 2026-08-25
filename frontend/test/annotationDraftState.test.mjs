import assert from 'node:assert/strict'
import test from 'node:test'

import {
  createArticleForm,
  createOverallForm,
  decideAnnotationNavigation,
  formsEqual,
  isArticleDraftComplete,
  parseAnnotationLocator,
  reconcileSavedForm,
  sameWorkbenchSession,
  selectInitialTarget,
} from '../src/views/annotation/annotationDraftState.ts'

const task = {
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
  assert.equal(parseAnnotationLocator('unknown.path'), null)
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
