import assert from 'node:assert/strict'
import test from 'node:test'

import { readAssignedReviewWithRetry } from '../src/views/review/reviewRecovery.ts'

const notStarted = { code: 'REVIEW.NOT_STARTED' }

test('领取竞态首次未读到 round 时短重试并返回后续 Review', async () => {
  let attempts = 0
  let waits = 0
  const review = { reviewRoundId: 'round-1', writable: false }

  const result = await readAssignedReviewWithRetry(
    async () => {
      attempts += 1
      if (attempts === 1) throw notStarted
      return review
    },
    (error) => error === notStarted,
    async () => { waits += 1 },
  )

  assert.equal(result, review)
  assert.equal(attempts, 2)
  assert.equal(waits, 1)
})

test('领取竞态达到三次读取上限后返回最后一次 NOT_STARTED', async () => {
  let attempts = 0
  let waits = 0

  await assert.rejects(
    readAssignedReviewWithRetry(
      async () => {
        attempts += 1
        throw notStarted
      },
      (error) => error === notStarted,
      async () => { waits += 1 },
    ),
    (error) => error === notStarted,
  )

  assert.equal(attempts, 3)
  assert.equal(waits, 2)
})

test('领取恢复遇到非 NOT_STARTED 错误时不重试', async () => {
  const networkError = new Error('network')
  let attempts = 0

  await assert.rejects(
    readAssignedReviewWithRetry(
      async () => {
        attempts += 1
        throw networkError
      },
      (error) => error === notStarted,
      async () => { throw new Error('不应等待') },
    ),
    (error) => error === networkError,
  )

  assert.equal(attempts, 1)
})
