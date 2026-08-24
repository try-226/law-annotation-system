import type { StructureNodeType, ValidityStatus } from './law'

export type TaskType = 'ORDINARY' | 'REVISION'

export type TaskState =
  | 'PENDING_ANNOTATION'
  | 'ANNOTATING'
  | 'PENDING_REVIEW'
  | 'PARTIALLY_REJECTED'
  | 'PENDING_REREVIEW'
  | 'APPROVED'
  | 'CANCELED'

export interface TaskQuery {
  taskName?: string
  taskType?: TaskType
  lawId?: string
  annotatorId?: string
  state?: TaskState
  page: number
  size: number
}

export interface TaskListItem {
  taskId: string
  taskName: string
  taskType: TaskType
  lawId: string
  lawName: string
  annotatorId: string
  annotatorName: string
  taskState: TaskState
  remark: string | null
  createdAt: string
}

export interface TaskArticleSnapshot {
  articleId: string
  number: string
  body: string
  order: number
}

export interface TaskContentVersionSnapshot {
  contentVersionId: string
  seq: number
  articles: TaskArticleSnapshot[]
}

export interface TaskLawBaseInfoSnapshot {
  name: string
  issuingAuthority: string
  publicationDate: string
  validityStatus: ValidityStatus
}

export interface TaskStructureNodeSnapshot {
  nodeId: string
  type: StructureNodeType
  title: string
  parentNodeId: string | null
  order: number
  articleIds: string[]
}

export interface TaskFieldConfigSnapshotItem {
  fieldKey: string
  required: boolean
}

export interface TaskFieldConfigSnapshot {
  overall: TaskFieldConfigSnapshotItem[]
  article: TaskFieldConfigSnapshotItem[]
}

export interface TaskDetail {
  taskId: string
  taskType: TaskType
  taskState: TaskState
  lawId: string
  annotatorId: string
  annotatorName: string
  taskName: string
  remark: string | null
  contentVersionId: string
  contentVersionSnapshot: TaskContentVersionSnapshot
  lawBaseInfoSnapshot: TaskLawBaseInfoSnapshot
  structureSnapshot: TaskStructureNodeSnapshot[]
  fieldConfigSnapshot: TaskFieldConfigSnapshot
  createdBy: string
  cancelReason: string | null
  canceledBy: string | null
  canceledAt: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateOrdinaryTaskPayload {
  lawId: string
  annotatorId: string
  taskName?: string
  remark?: string
}

export interface CancelTaskPayload {
  reason: string
}

export const TASK_STATE_LABELS: Record<TaskState, string> = {
  PENDING_ANNOTATION: '待标注',
  ANNOTATING: '标注中',
  PENDING_REVIEW: '待审核',
  PARTIALLY_REJECTED: '部分驳回',
  PENDING_REREVIEW: '待复审',
  APPROVED: '已通过',
  CANCELED: '已取消',
}

export const TASK_TYPE_LABELS: Record<TaskType, string> = {
  ORDINARY: '普通标注',
  REVISION: '修订',
}

export const ANNOTATOR_TASK_ACTION_LABELS: Record<TaskState, string> = {
  PENDING_ANNOTATION: '开始',
  ANNOTATING: '继续',
  PARTIALLY_REJECTED: '修改',
  PENDING_REVIEW: '查看',
  PENDING_REREVIEW: '查看',
  APPROVED: '查看',
  CANCELED: '查看',
}

export const UNFINISHED_TASK_STATES: readonly TaskState[] = [
  'PENDING_ANNOTATION',
  'ANNOTATING',
  'PENDING_REVIEW',
  'PARTIALLY_REJECTED',
  'PENDING_REREVIEW',
]

export function isCancelableTaskState(state: TaskState): boolean {
  return state === 'PENDING_ANNOTATION' || state === 'ANNOTATING'
}

export function isUnfinishedTaskState(state: TaskState): boolean {
  return UNFINISHED_TASK_STATES.includes(state)
}

export function formatTaskDateTime(value: string | null): string {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '--'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}
