import assert from 'node:assert/strict'
import test from 'node:test'

import {
  annotationArticleRows,
  formatAuditValue,
  historyDetailRoute,
  historyTimelineRows,
} from '../src/views/history/historyPresentation.ts'

test('历史详情导航使用 named route 和未编码 params', () => {
  assert.deepEqual(historyDetailRoute('law/一', { type: 'CONTENT_VERSION', resourceId: 'content/1' }), {
    name: 'history-content-version',
    params: { lawId: 'law/一', contentVersionId: 'content/1' },
  })
  assert.deepEqual(historyDetailRoute('law/一', { type: 'ANNOTATION_VERSION', resourceId: 'annotation/1' }), {
    name: 'history-annotation-version',
    params: { lawId: 'law/一', annotationVersionId: 'annotation/1' },
  })
  assert.deepEqual(historyDetailRoute('law/一', { type: 'LAW_AUDIT', resourceId: 'audit/1' }), {
    name: 'history-law-audit',
    params: { lawId: 'law/一', auditId: 'audit/1' },
  })
  assert.deepEqual(historyDetailRoute('law/一', { type: 'TASK', resourceId: 'task/1' }), {
    name: 'task-history',
    params: { lawId: 'law/一', taskId: 'task/1' },
  })
  assert.equal(historyDetailRoute('law/一', { type: 'UNKNOWN', resourceId: 'unknown/1' }), null)
})

test('法律时间线仅附加详情路由并保持服务端原始顺序', () => {
  const timeline = [
    { eventId: 'newest', detailRef: { type: 'TASK', resourceId: 'task-2' } },
    { eventId: 'middle', detailRef: { type: 'LAW_AUDIT', resourceId: 'audit-1' } },
    { eventId: 'oldest', detailRef: { type: 'CONTENT_VERSION', resourceId: 'content-1' } },
  ]

  const rows = historyTimelineRows('law-1', timeline)

  assert.deepEqual(rows.map((item) => item.eventId), ['newest', 'middle', 'oldest'])
  assert.deepEqual(rows.map((item) => item.route.name), [
    'task-history',
    'history-law-audit',
    'history-content-version',
  ])
})

test('标注版本按绑定的历史内容快照排序并映射条号正文', () => {
  const rows = annotationArticleRows(
    [
      { articleId: 'article-2', values: { itemType: 'OTHER', keywords: '乙', subjects: null, legalLiability: null, annotationNote: null } },
      { articleId: 'article-1', values: { itemType: 'DEFINITION', keywords: '甲', subjects: null, legalLiability: null, annotationNote: null } },
    ],
    [
      { articleId: 'article-1', number: '第一条', body: '历史正文一', order: 1 },
      { articleId: 'article-2', number: '第二条', body: '历史正文二', order: 2 },
    ],
  )

  assert.deepEqual(rows.map(({ articleId, number, body }) => ({ articleId, number, body })), [
    { articleId: 'article-1', number: '第一条', body: '历史正文一' },
    { articleId: 'article-2', number: '第二条', body: '历史正文二' },
  ])
})

test('任务提交法条只使用传入的任务内容快照映射', () => {
  const rows = annotationArticleRows(
    [{ articleId: 'article-1', values: { itemType: 'OTHER', keywords: '历史标注', subjects: null, legalLiability: null, annotationNote: null } }],
    [{ articleId: 'article-1', number: '历史第一条', body: '任务创建时正文', order: 1 }],
  )

  assert.deepEqual(rows.map(({ number, body }) => ({ number, body })), [
    { number: '历史第一条', body: '任务创建时正文' },
  ])
})

test('审计 unknown 快照安全格式化且保留嵌套值', () => {
  assert.equal(formatAuditValue({ name: '旧名称', nested: { enabled: false }, values: [1, null] }), [
    '{',
    '  "name": "旧名称",',
    '  "nested": {',
    '    "enabled": false',
    '  },',
    '  "values": [',
    '    1,',
    '    null',
    '  ]',
    '}',
  ].join('\n'))
})
