import type { RouteRecordName } from 'vue-router'

import type { DashboardSummary } from '../../types/dashboard'

export interface DashboardCard {
  key: keyof DashboardSummary
  label: string
  value: number
  routeName: RouteRecordName
}

const CARD_DEFINITIONS: ReadonlyArray<{
  key: keyof DashboardSummary
  label: string
  routeName: RouteRecordName
}> = [
  { key: 'totalLaws', label: '法律文件总数', routeName: 'law-list' },
  { key: 'totalArticles', label: '法条总数', routeName: 'law-list' },
  { key: 'unannotatedLaws', label: '未标注法律', routeName: 'law-list' },
  { key: 'inProgressTasks', label: '进行中任务', routeName: 'admin-tasks' },
  { key: 'pendingReviewTasks', label: '待审核任务', routeName: 'admin-tasks' },
  { key: 'pendingRereviewTasks', label: '待复审任务', routeName: 'admin-tasks' },
  { key: 'pendingRevisionLaws', label: '待修订法律', routeName: 'law-list' },
  { key: 'completedLaws', label: '已完成法律', routeName: 'law-list' },
]

export function dashboardCards(summary: DashboardSummary): DashboardCard[] {
  return CARD_DEFINITIONS.map((definition) => ({
    ...definition,
    value: summary[definition.key],
  }))
}
