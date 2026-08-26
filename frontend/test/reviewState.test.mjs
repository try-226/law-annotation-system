import assert from 'node:assert/strict'
import test from 'node:test'

import {
  buildReviewFieldRows,
  buildReviewTargetOrder,
  canCompleteReview,
  findNextReviewTarget,
  findNextUnreviewedTarget,
  normalizeIssueReason,
  reviewFailureDecision,
  reviewTargetCapabilities,
  reviewTargetKey,
  searchReviewArticles,
  selectInitialReviewTarget,
  validateIssueReason,
} from '../src/views/review/reviewState.ts'

const articles = [
  { articleId: 'a-2', number: '第二条', body: '第二条正文包含（特殊）字符', order: 2 },
  { articleId: 'a-1', number: '第一条', body: '第一条正文', order: 1 },
  { articleId: 'a-3', number: '第三条', body: '第三条正文', order: 3 },
]

function item(target, state, reason = null) {
  return {
    locator: target.kind === 'overall'
      ? { type: 'OVERALL', articleId: null }
      : { type: 'ARTICLE', articleId: target.articleId },
    state,
    issue: reason
      ? {
          reviewRoundId: 'round-1', taskId: 'task-1', scopeType: target.kind === 'overall' ? 'OVERALL' : 'ARTICLE',
          articleId: target.kind === 'overall' ? null : target.articleId, reason, createdAt: '2026-08-26T00:00:00Z',
        }
      : null,
  }
}

function detail(overrides = {}) {
  return {
    taskId: 'task-1', reviewRoundId: 'round-1', roundNo: 1, roundType: 'INITIAL_REVIEW',
    taskState: 'PENDING_REVIEW', reviewerId: 'admin-1', writable: true,
    progress: { total: 4, reviewed: 1, unreviewed: 3, needsChange: 0 },
    items: [
      item({ kind: 'overall' }, 'CHECKED'),
      item({ kind: 'article', articleId: 'a-1' }, 'UNREVIEWED'),
      item({ kind: 'article', articleId: 'a-2' }, 'UNREVIEWED'),
      item({ kind: 'article', articleId: 'a-3' }, 'UNREVIEWED'),
    ],
    contentVersionSnapshot: { contentVersionId: 'content-1', seq: 1, articles },
    lawBaseInfoSnapshot: { name: '测试法', issuingAuthority: '测试机关', publicationDate: '2026-08-26', validityStatus: 'ACTIVE' },
    structureSnapshot: [], fieldConfigSnapshot: { overall: [], article: [] },
    before: null,
    after: {
      submissionId: 'submission-1', submissionNo: 1,
      overall: { lawCategory: '民事', overallKeywords: '测试', summary: null, overallNote: null },
      articles: {}, submittedAt: '2026-08-26T00:00:00Z',
    },
    outcome: null, annotationVersionId: null, startedAt: '2026-08-26T00:00:00Z',
    completionStartedAt: null, completedAt: null,
    ...overrides,
  }
}

test('审核目录始终按整体信息和法条 order 排序', () => {
  assert.deepEqual(buildReviewTargetOrder(detail()), [
    { kind: 'overall' },
    { kind: 'article', articleId: 'a-1' },
    { kind: 'article', articleId: 'a-2' },
    { kind: 'article', articleId: 'a-3' },
  ])
})

test('重新进入进行中的审核定位第一项服务器未审核内容', () => {
  assert.deepEqual(selectInitialReviewTarget(detail()), { kind: 'article', articleId: 'a-1' })
})

test('完成轮次无未审核项时优先定位第一项问题', () => {
  const review = detail({
    writable: false, taskState: 'PARTIALLY_REJECTED', completedAt: '2026-08-26T01:00:00Z', outcome: 'PARTIALLY_REJECTED',
    items: [
      item({ kind: 'overall' }, 'CHECKED'),
      item({ kind: 'article', articleId: 'a-1' }, 'NEEDS_CHANGE', '仍需修改'),
      item({ kind: 'article', articleId: 'a-2' }, 'CHECKED'),
      item({ kind: 'article', articleId: 'a-3' }, 'CHECKED'),
    ],
  })
  assert.deepEqual(selectInitialReviewTarget(review), { kind: 'article', articleId: 'a-1' })
})

test('下一未审核项从当前项之后查找并最多回绕一次', () => {
  const review = detail({
    items: [
      item({ kind: 'overall' }, 'UNREVIEWED'),
      item({ kind: 'article', articleId: 'a-1' }, 'CHECKED'),
      item({ kind: 'article', articleId: 'a-2' }, 'CHECKED'),
      item({ kind: 'article', articleId: 'a-3' }, 'UNREVIEWED'),
    ],
  })
  assert.deepEqual(findNextUnreviewedTarget(review, { kind: 'article', articleId: 'a-2' }), { kind: 'article', articleId: 'a-3' })
  assert.deepEqual(findNextUnreviewedTarget(review, { kind: 'article', articleId: 'a-3' }), { kind: 'overall' })
})

test('跳过唯一未审核项时不返回当前项形成循环', () => {
  const review = detail({
    items: [
      item({ kind: 'overall' }, 'CHECKED'),
      item({ kind: 'article', articleId: 'a-1' }, 'CHECKED'),
      item({ kind: 'article', articleId: 'a-2' }, 'UNREVIEWED'),
      item({ kind: 'article', articleId: 'a-3' }, 'CHECKED'),
    ],
  })
  assert.equal(findNextUnreviewedTarget(review, { kind: 'article', articleId: 'a-2' }), null)
})

test('纯下一项按目录顺序导航，不跳过已处理或非 scope 项', () => {
  const review = detail({
    roundType: 'REREVIEW',
    items: [item({ kind: 'overall' }, 'CHECKED')],
  })
  assert.deepEqual(findNextReviewTarget(review, { kind: 'overall' }), { kind: 'article', articleId: 'a-1' })
  assert.deepEqual(findNextReviewTarget(review, { kind: 'article', articleId: 'a-1' }), { kind: 'article', articleId: 'a-2' })
  assert.equal(findNextReviewTarget(review, { kind: 'article', articleId: 'a-3' }), null)
})

test('复审非 scope 项只能由当前 reviewer 新增问题', () => {
  const review = detail({
    roundType: 'REREVIEW', taskState: 'PENDING_REREVIEW',
    items: [item({ kind: 'article', articleId: 'a-1' }, 'UNREVIEWED')],
  })
  assert.deepEqual(reviewTargetCapabilities(review, { kind: 'article', articleId: 'a-2' }), {
    inScope: false, state: null, canCheck: false, canIssue: true, canCheckAndNext: false,
  })
})

test('只读或已完成轮次不能产生任何写操作', () => {
  const target = { kind: 'article', articleId: 'a-1' }
  assert.equal(reviewTargetCapabilities(detail({ writable: false }), target).canIssue, false)
  assert.equal(reviewTargetCapabilities(detail({ completedAt: '2026-08-26T01:00:00Z' }), target).canCheck, false)
})

test('已核查项不允许下一项并核查覆盖结论', () => {
  assert.equal(reviewTargetCapabilities(detail(), { kind: 'overall' }).canCheckAndNext, false)
  assert.equal(reviewTargetCapabilities(detail(), { kind: 'article', articleId: 'a-1' }).canCheckAndNext, true)
})

test('已有问题项不能被核查按钮覆盖，只能修改问题或纯导航', () => {
  const review = detail({
    items: [item({ kind: 'article', articleId: 'a-1' }, 'NEEDS_CHANGE', '仍需修改')],
  })
  const capability = reviewTargetCapabilities(review, { kind: 'article', articleId: 'a-1' })
  assert.equal(capability.canCheck, false)
  assert.equal(capability.canCheckAndNext, false)
  assert.equal(capability.canIssue, true)
})

test('完成操作同时受可写、完成中、已完成和未审核数保护', () => {
  const ready = detail({ progress: { total: 4, reviewed: 4, unreviewed: 0, needsChange: 1 } })
  assert.equal(canCompleteReview(ready), true)
  assert.equal(canCompleteReview({ ...ready, writable: false }), false)
  assert.equal(canCompleteReview({ ...ready, completionStartedAt: '2026-08-26T01:00:00Z' }), false)
  assert.equal(canCompleteReview({ ...ready, completedAt: '2026-08-26T01:00:00Z' }), false)
  assert.equal(canCompleteReview(detail()), false)
})

test('问题原因按 Unicode 码点校验 trim 后 1 至 500 字符', () => {
  assert.match(validateIssueReason('   '), /不能为空/)
  assert.equal(validateIssueReason(` ${'😀'.repeat(500)} `), null)
  assert.match(validateIssueReason('😀'.repeat(501)), /500/)
  assert.match(validateIssueReason('第一行\n第二行'), /控制字符/)
  assert.equal(normalizeIssueReason('  具体问题  '), '具体问题')
})

test('字段级对照把枚举转成标签并标记变化字段', () => {
  assert.deepEqual(buildReviewFieldRows(
    'article',
    { itemType: 'DEFINITION', keywords: '旧关键词', subjects: null, legalLiability: null, annotationNote: null },
    { itemType: 'PROCEDURE', keywords: '旧关键词', subjects: null, legalLiability: '新责任', annotationNote: null },
  ), [
    { key: 'itemType', label: '条目类型', before: '定义解释类', after: '程序规则类', changed: true },
    { key: 'keywords', label: '关键词', before: '旧关键词', after: '旧关键词', changed: false },
    { key: 'subjects', label: '涉及主体', before: '—', after: '—', changed: false },
    { key: 'legalLiability', label: '法律责任', before: '—', after: '新责任', changed: true },
    { key: 'annotationNote', label: '标注备注', before: '—', after: '—', changed: false },
  ])
})

test('审核搜索仅按冻结法条条号和正文做普通文本匹配', () => {
  assert.deepEqual(searchReviewArticles(articles, '（特殊）').map((article) => article.articleId), ['a-2'])
  assert.deepEqual(searchReviewArticles(articles, '第一条').map((article) => article.articleId), ['a-1'])
  assert.deepEqual(searchReviewArticles(articles, '   '), [])
})

test('目标 key 稳定区分整体与法条', () => {
  assert.equal(reviewTargetKey({ kind: 'overall' }), 'overall')
  assert.equal(reviewTargetKey({ kind: 'article', articleId: 'a-1' }), 'article:a-1')
})

test('审核错误码决定保留输入或刷新服务器状态', () => {
  assert.equal(reviewFailureDecision('REVIEW.NOT_STARTED'), 'not-started')
  assert.equal(reviewFailureDecision('REVIEW.ISSUE_REASON_INVALID'), 'reason')
  assert.equal(reviewFailureDecision('REVIEW.ALREADY_ASSIGNED'), 'reload')
  assert.equal(reviewFailureDecision('REVIEW.COMPLETION_CONFLICT'), 'reload')
  assert.equal(reviewFailureDecision('REVIEW.SOURCE_INVALID'), 'fatal')
  assert.equal(reviewFailureDecision(undefined), 'fatal')
})
