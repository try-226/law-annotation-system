import assert from 'node:assert/strict'
import test from 'node:test'

import {
  annotationVersionHistoryPath,
  contentVersionHistoryPath,
  lawAuditHistoryPath,
  lawHistoryPath,
  taskHistoryPath,
} from '../src/api/historyPath.ts'

test('History API helper 对每个 path segment 单独编码', () => {
  assert.equal(lawHistoryPath('law/一'), '/laws/law%2F%E4%B8%80/history')
  assert.equal(
    contentVersionHistoryPath('law/一', 'content/1'),
    '/laws/law%2F%E4%B8%80/history/content-versions/content%2F1',
  )
  assert.equal(
    annotationVersionHistoryPath('law/一', 'annotation/1'),
    '/laws/law%2F%E4%B8%80/history/annotation-versions/annotation%2F1',
  )
  assert.equal(
    lawAuditHistoryPath('law/一', 'audit/1'),
    '/laws/law%2F%E4%B8%80/history/audits/audit%2F1',
  )
  assert.equal(
    taskHistoryPath('law/一', 'task/1'),
    '/laws/law%2F%E4%B8%80/history/tasks/task%2F1',
  )
})
