import type { TaskState, TaskType } from './task'

export interface DashboardSummary {
  totalLaws: number
  totalArticles: number
  unannotatedLaws: number
  inProgressTasks: number
  pendingReviewTasks: number
  pendingRereviewTasks: number
  pendingRevisionLaws: number
  completedLaws: number
}

export interface DashboardTodoItem {
  taskId: string
  taskName: string
  taskType: TaskType
  lawId: string
  lawName: string
  taskState: TaskState
  updatedAt: string
}

export interface DashboardTodos {
  pendingReview: DashboardTodoItem[]
  pendingRereview: DashboardTodoItem[]
}
