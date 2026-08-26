import assert from 'node:assert/strict'
import test from 'node:test'

import { reviewItemPath } from '../src/api/reviewPath.ts'

test('整体审核项使用 overall 合同路径', () => {
  assert.equal(reviewItemPath('task-1', 'round-1', { kind: 'overall' }), '/tasks/task-1/review/rounds/round-1/overall')
})

test('法条审核项对 articleId 编码后使用 articles 合同路径', () => {
  assert.equal(
    reviewItemPath('task 1', 'round/1', { kind: 'article', articleId: 'article/一' }),
    '/tasks/task%201/review/rounds/round%2F1/articles/article%2F%E4%B8%80',
  )
})
