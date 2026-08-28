import assert from 'node:assert/strict'
import test from 'node:test'

import { isRetryableFailure } from '../src/utils/failurePolicy.ts'

function failure(status, code = 'TEST.ERROR') {
  return { status, code, userMessage: '测试错误', locators: [], network: false }
}

test('无响应网络错误和服务端错误允许重新加载', () => {
  assert.equal(isRetryableFailure({ locators: [], network: true }), true)
  assert.equal(isRetryableFailure(failure(500)), true)
  assert.equal(isRetryableFailure(failure(503)), true)
})

test('权限、隐藏资源和历史快照冲突不显示无意义重试', () => {
  assert.equal(isRetryableFailure(failure(403, 'AUTH.FORBIDDEN')), false)
  assert.equal(isRetryableFailure(failure(404, 'TASK.NOT_FOUND')), false)
  assert.equal(isRetryableFailure(failure(409, 'HISTORY.SNAPSHOT_INCONSISTENT')), false)
})
