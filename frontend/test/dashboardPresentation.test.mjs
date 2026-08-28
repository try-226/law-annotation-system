import assert from 'node:assert/strict'
import test from 'node:test'

import { dashboardCards } from '../src/views/dashboard/dashboardPresentation.ts'

test('工作台统计卡片直接展示 summary 八字段并只导航到现有页面', () => {
  const cards = dashboardCards({
    totalLaws: 86,
    totalArticles: 12486,
    unannotatedLaws: 12,
    inProgressTasks: 8,
    pendingReviewTasks: 5,
    pendingRereviewTasks: 2,
    pendingRevisionLaws: 3,
    completedLaws: 71,
  })

  assert.deepEqual(cards.map(({ label, value, routeName }) => ({ label, value, routeName })), [
    { label: '法律文件总数', value: 86, routeName: 'law-list' },
    { label: '法条总数', value: 12486, routeName: 'law-list' },
    { label: '未标注法律', value: 12, routeName: 'law-list' },
    { label: '进行中任务', value: 8, routeName: 'admin-tasks' },
    { label: '待审核任务', value: 5, routeName: 'admin-tasks' },
    { label: '待复审任务', value: 2, routeName: 'admin-tasks' },
    { label: '待修订法律', value: 3, routeName: 'law-list' },
    { label: '已完成法律', value: 71, routeName: 'law-list' },
  ])
})
