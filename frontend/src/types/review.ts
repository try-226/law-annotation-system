import type { ArticleDraftValues, OverallDraftValues } from './annotation'
import type {
  TaskContentVersionSnapshot,
  TaskFieldConfigSnapshot,
  TaskLawBaseInfoSnapshot,
  TaskState,
  TaskStructureNodeSnapshot,
} from './task'

export type ReviewRoundType = 'INITIAL_REVIEW' | 'REREVIEW'
export type ReviewItemState = 'UNREVIEWED' | 'CHECKED' | 'NEEDS_CHANGE'
export type ReviewOutcome = 'APPROVED' | 'PARTIALLY_REJECTED'
export type ReviewScopeType = 'OVERALL' | 'ARTICLE'

export type ReviewTarget =
  | { kind: 'overall' }
  | { kind: 'article'; articleId: string }

export interface ReviewItemLocator {
  type: ReviewScopeType
  articleId: string | null
}

export interface ReviewIssue {
  reviewRoundId: string
  taskId: string
  scopeType: ReviewScopeType
  articleId: string | null
  reason: string
  createdAt: string
}

export interface ReviewItem {
  locator: ReviewItemLocator
  state: ReviewItemState
  issue: ReviewIssue | null
}

export interface ReviewProgress {
  total: number
  reviewed: number
  unreviewed: number
  needsChange: number
}

export interface ReviewSubmissionSnapshot {
  submissionId: string
  submissionNo: number
  overall: OverallDraftValues
  articles: Record<string, ArticleDraftValues>
  submittedAt: string
}

export interface ReviewDetail {
  taskId: string
  reviewRoundId: string
  roundNo: number
  roundType: ReviewRoundType
  taskState: TaskState
  reviewerId: string
  writable: boolean
  progress: ReviewProgress
  items: ReviewItem[]
  contentVersionSnapshot: TaskContentVersionSnapshot
  lawBaseInfoSnapshot: TaskLawBaseInfoSnapshot
  structureSnapshot: TaskStructureNodeSnapshot[]
  fieldConfigSnapshot: TaskFieldConfigSnapshot
  before: ReviewSubmissionSnapshot | null
  after: ReviewSubmissionSnapshot
  outcome: ReviewOutcome | null
  annotationVersionId: string | null
  startedAt: string
  completionStartedAt: string | null
  completedAt: string | null
}

export interface ReviewFieldRow {
  key: string
  label: string
  before: string
  after: string
  changed: boolean
}

export interface ReviewTargetCapabilities {
  inScope: boolean
  state: ReviewItemState | null
  canCheck: boolean
  canIssue: boolean
  canCheckAndNext: boolean
}
