import assert from 'node:assert/strict'
import test from 'node:test'

import {
  isAnnotationHit,
  SEARCH_SCOPE_OPTIONS,
  searchFieldLabel,
  searchResultRoute,
  splitSearchSnippet,
} from '../src/views/search/searchPresentation.ts'
import { normalizeSearchQuery, validateRequiredSearch } from '../src/utils/validation.ts'

function hit(changes = {}) {
  return {
    lawId: 'law/一',
    lawName: '测试法',
    articleId: 'article/一',
    articleNumber: '第一条',
    structurePath: ['第一章'],
    hitSource: 'ARTICLE_BODY',
    hitField: 'article.body',
    snippet: '前缀.*+?[]()\\$^后缀',
    highlightStart: 2,
    highlightEnd: 13,
    ...changes,
  }
}

test('搜索词空白、长度和规范化符合 1..100 合同', () => {
  assert.equal(validateRequiredSearch('  \n\t  '), '请输入搜索关键词')
  assert.match(validateRequiredSearch('😀'.repeat(101)), /100/)
  assert.equal(validateRequiredSearch('  行政\n 许可  '), null)
  assert.equal(normalizeSearchQuery('  行政\n 许可  '), '行政 许可')
})

test('搜索范围严格映射后端 SearchScope', () => {
  assert.deepEqual(SEARCH_SCOPE_OPTIONS.map((item) => item.value), ['ALL', 'LAW_TEXT', 'ANNOTATION'])
})

test('高亮只按服务端偏移分段且不解释正则特殊字符', () => {
  assert.deepEqual(splitSearchSnippet(hit()), [
    { text: '前缀', highlighted: false },
    { text: '.*+?[]()\\$^', highlighted: true },
    { text: '后缀', highlighted: false },
  ])
})

test('潜在 HTML 仅作为文本 segment 返回', () => {
  const snippet = '<img src=x onerror=alert(1)>'
  const segments = splitSearchSnippet(hit({ snippet, highlightStart: 1, highlightEnd: 4 }))
  assert.equal(segments.map((segment) => segment.text).join(''), snippet)
  assert.deepEqual(segments[1], { text: 'img', highlighted: true })
})

test('搜索结果使用 named route 定位法律、法条和正式结果', () => {
  assert.deepEqual(searchResultRoute(hit()), {
    name: 'law-detail', params: { lawId: 'law/一' }, query: { articleId: 'article/一' },
  })
  assert.deepEqual(searchResultRoute(hit(), true), {
    name: 'law-detail', params: { lawId: 'law/一' }, query: { articleId: 'article/一', section: 'formal' },
  })
  assert.equal(searchFieldLabel('overallAnnotation.summary'), '摘要')
  assert.equal(isAnnotationHit(hit({ hitSource: 'ARTICLE_ANNOTATION' })), true)
  assert.equal(isAnnotationHit(hit()), false)
})
