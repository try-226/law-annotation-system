import assert from 'node:assert/strict'
import test from 'node:test'

import { annotationSubmissionPath } from '../src/api/annotationPath.ts'

test('初次提交和部分驳回提交使用 PR17 真实端点', () => {
  assert.equal(annotationSubmissionPath('task-1', 'review'), '/tasks/task-1/submit-review')
  assert.equal(annotationSubmissionPath('task-1', 'rereview'), '/tasks/task-1/submit-rereview')
})
