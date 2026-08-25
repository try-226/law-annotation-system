import assert from 'node:assert/strict'
import test from 'node:test'

import { searchTask } from '../src/views/annotation/annotationSearch.ts'

const task = {
  lawBaseInfoSnapshot: { name: '测试法' },
  contentVersionSnapshot: {
    articles: [
      { articleId: 'a-1', number: '第一条', body: '法律正文包含 . * + [ ( ? 符号', order: 1 },
      { articleId: 'a-2', number: '第二条', body: '其他正文', order: 2 },
    ],
  },
  structureSnapshot: [
    { nodeId: 'book', title: '总则编', parentNodeId: null, order: 1, articleIds: [] },
    { nodeId: 'chapter', title: '总则章', parentNodeId: 'book', order: 1, articleIds: [] },
    { nodeId: 'section', title: '总则节', parentNodeId: 'chapter', order: 1, articleIds: ['a-1', 'a-2'] },
  ],
}

const draft = {
  overallDraft: { lawCategory: '民事', overallKeywords: '合同', summary: '整体摘要', overallNote: null },
  articleDrafts: {
    'a-1': { itemType: 'DEFINITION', keywords: '合同', subjects: '当事人', legalLiability: null, annotationNote: null },
    'a-2': { itemType: 'RIGHTS_DUTIES', keywords: '权利', subjects: null, legalLiability: null, annotationNote: null },
  },
}

test('空查询表示清空搜索且不报错', () => {
  assert.deepEqual(searchTask(task, draft, { query: '   ', scope: 'ALL', page: 1, size: 10 }), {
    active: false, query: '', error: null, items: [], page: 1, size: 10, totalElements: 0, totalPages: 0,
  })
})

test('非空查询按 Unicode code points 和控制字符校验', () => {
  assert.match(searchTask(task, draft, { query: '😀'.repeat(101), scope: 'ALL', page: 1, size: 10 }).error, /100/)
  assert.match(searchTask(task, draft, { query: '总\n则', scope: 'ALL', page: 1, size: 10 }).error, /控制字符/)
})

test('正则特殊字符仅按普通文本搜索', () => {
  for (const query of ['.', '*', '+', '[', '(', '?']) {
    const result = searchTask(task, draft, { query, scope: 'CONTENT', page: 1, size: 10 })
    assert.equal(result.error, null)
    assert.equal(result.items[0].articleId, 'a-1')
    assert.equal(result.items[0].fieldKey, 'articleBody')
  }
})

test('搜索范围严格区分法律正文和服务器已保存标注结果', () => {
  assert.equal(searchTask(task, draft, { query: '法律正文', scope: 'ANNOTATION', page: 1, size: 10 }).totalElements, 0)
  assert.equal(searchTask(task, draft, { query: '整体摘要', scope: 'CONTENT', page: 1, size: 10 }).totalElements, 0)
  const annotation = searchTask(task, draft, { query: '整体摘要', scope: 'ANNOTATION', page: 1, size: 10 })
  assert.equal(annotation.items[0].target.kind, 'overall')
})

test('条目类型使用中文标签搜索和显示', () => {
  const result = searchTask(task, draft, { query: '定义解释类', scope: 'ANNOTATION', page: 1, size: 10 })
  assert.equal(result.totalElements, 1)
  assert.equal(result.items[0].displayText, '定义解释类')
  assert.equal(result.items[0].fieldKey, 'itemType')
})

test('多个匹配祖先结构标题只为每条法条产生一个稳定结果', () => {
  const first = searchTask(task, draft, { query: '总则', scope: 'CONTENT', page: 1, size: 10 })
  const second = searchTask(task, draft, { query: '总则', scope: 'CONTENT', page: 1, size: 10 })
  assert.equal(first.totalElements, 2)
  assert.deepEqual(first.items.map((item) => item.key), [
    'article:a-1|structureTitle', 'article:a-2|structureTitle',
  ])
  assert.deepEqual(first.items.map((item) => item.key), second.items.map((item) => item.key))
})

test('结果稳定排序、默认10条分页并使用安全 segments 高亮', () => {
  const manyDrafts = {
    ...draft,
    overallDraft: { ...draft.overallDraft, overallKeywords: '合同', summary: '合同合同' },
    articleDrafts: {
      ...draft.articleDrafts,
      'a-1': { ...draft.articleDrafts['a-1'], keywords: '合同', annotationNote: '合同' },
    },
  }
  const result = searchTask(task, manyDrafts, { query: '合同', scope: 'ANNOTATION', page: 1, size: 2 })
  assert.equal(result.size, 2)
  assert.equal(result.totalPages, 3)
  assert.deepEqual(result.items.map((item) => item.target.kind), ['overall', 'overall'])
  assert.ok(result.items[0].segments.some((segment) => segment.highlighted && segment.text === '合同'))
})
