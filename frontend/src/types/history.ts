import type { ArticleDraftValues, OverallDraftValues } from './annotation'
import type { ReviewItemLocator, ReviewItemState, ReviewOutcome, ReviewRoundType } from './review'
import type {
  RevisionScope,
  TaskArticleSnapshot,
  TaskContentVersionSnapshot,
  TaskFieldConfigSnapshot,
  TaskLawBaseInfoSnapshot,
  TaskState,
  TaskStructureNodeSnapshot,
  TaskType,
} from './task'

export type HistoryCategory =
  | 'CONTENT_VERSION'
  | 'LAW_AUDIT'
  | 'ANNOTATION_VERSION'
  | 'TASK'
  | 'SUBMISSION'
  | 'REVIEW'
  | 'CANCELLATION'

export type HistoryItemType =
  | 'CONTENT_VERSION_CREATED'
  | 'LAW_METADATA_CHANGED'
  | 'LAW_STRUCTURE_CHANGED'
  | 'ANNOTATION_VERSION_APPROVED'
  | 'TASK_CREATED'
  | 'TASK_SUBMITTED'
  | 'TASK_REREVIEW_SUBMITTED'
  | 'TASK_CANCELED'
  | 'REVIEW_STARTED'
  | 'REVIEW_ISSUE_CREATED'
  | 'REVIEW_COMPLETED'

export type HistoryDetailType = 'CONTENT_VERSION' | 'LAW_AUDIT' | 'ANNOTATION_VERSION' | 'TASK'

export interface HistoryDetailRef {
  type: HistoryDetailType
  resourceId: string
}

export interface HistoryTimelineItem {
  eventId: string
  category: HistoryCategory
  type: HistoryItemType
  entityId: string
  taskId: string | null
  actorId: string | null
  occurredAt: string
  summary: string
  detailRef: HistoryDetailRef
}

export interface LawHistory {
  lawId: string
  deleted: boolean
  deletedAt: string | null
  timeline: HistoryTimelineItem[]
}

export interface ContentVersionHistory {
  contentVersionId: string
  lawId: string
  seq: number
  semanticArticlesSnapshot: TaskArticleSnapshot[]
  createdBy: string
  createdAt: string
}

export interface AnnotationVersionArticleResult {
  articleId: string
  values: ArticleDraftValues
}

export interface AnnotationVersionHistory {
  annotationVersionId: string
  lawId: string
  seq: number
  contentVersionId: string
  overallResult: OverallDraftValues
  articleResults: AnnotationVersionArticleResult[]
  sourceTaskId: string
  sourceSubmissionId: string
  approvedBy: string
  approvedAt: string
}

export type LawAuditType = 'BASE_INFO' | 'STRUCTURE'

export interface LawAuditHistory {
  auditId: string
  lawId: string
  auditType: LawAuditType
  before: Record<string, unknown>
  after: Record<string, unknown>
  operatorId: string
  operatedAt: string
}

export interface TaskHistorySubmission {
  submissionId: string
  submissionNo: number
  draftRevision: number
  overallSnapshot: OverallDraftValues
  articleSnapshots: AnnotationVersionArticleResult[]
  sourceReviewRoundId: string | null
  modifiedScope: ReviewItemLocator[]
  submittedBy: string
  submittedAt: string
}

export interface TaskHistoryItemState {
  locator: ReviewItemLocator
  state: ReviewItemState
}

export interface TaskHistoryIssue {
  locator: ReviewItemLocator
  reason: string
  actorId: string
  createdAt: string
}

export interface TaskHistoryReviewRound {
  reviewRoundId: string
  roundNo: number
  roundType: ReviewRoundType
  sourceSubmissionId: string
  previousSubmissionId: string | null
  reviewerId: string | null
  requiredScope: ReviewItemLocator[]
  itemStates: TaskHistoryItemState[]
  issues: TaskHistoryIssue[]
  totalCount: number
  reviewedCount: number
  unreviewedCount: number
  needsChangeCount: number
  completionOutcome: ReviewOutcome | null
  completionStartedAt: string | null
  annotationVersionId: string | null
  createdAt: string
  startedAt: string | null
  completedAt: string | null
}

export interface TaskHistory {
  taskId: string
  taskType: TaskType
  taskState: TaskState
  taskName: string
  remark: string | null
  lawId: string
  lawDeleted: boolean
  lawDeletedAt: string | null
  annotatorId: string
  annotatorNameSnapshot: string
  createdBy: string
  createdAt: string
  updatedAt: string
  contentVersionId: string
  contentVersionSnapshot: TaskContentVersionSnapshot
  lawBaseInfoSnapshot: TaskLawBaseInfoSnapshot
  structureSnapshot: TaskStructureNodeSnapshot[]
  fieldConfigSnapshot: TaskFieldConfigSnapshot
  baseAnnotationVersionId: string | null
  revisionScope: RevisionScope | null
  initialSubmissionId: string | null
  currentSubmissionId: string | null
  currentReviewRoundId: string | null
  approvedAnnotationVersionId: string | null
  cancelReason: string | null
  canceledBy: string | null
  canceledAt: string | null
  submissions: TaskHistorySubmission[]
  reviewRounds: TaskHistoryReviewRound[]
}
