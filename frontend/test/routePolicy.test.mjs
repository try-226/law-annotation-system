import assert from 'node:assert/strict'
import test from 'node:test'

import { landingRouteName, roleCanAccess } from '../src/router/routePolicy.ts'

test('管理员登录落到工作台而标注员仍落到我的任务', () => {
  assert.equal(landingRouteName('ADMIN'), 'dashboard')
  assert.equal(landingRouteName('ANNOTATOR'), 'my-tasks')
})

test('管理员搜索路由仅允许 ADMIN', () => {
  assert.equal(roleCanAccess('ADMIN', ['ADMIN']), true)
  assert.equal(roleCanAccess('ANNOTATOR', ['ADMIN']), false)
})
