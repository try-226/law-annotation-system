import type { ErrorLocator } from '../api/types'
import type { ReviewItemLocator } from './review'
import type { TaskState } from './task'

export type ItemType =
  | 'DEFINITION'
  | 'RIGHTS_DUTIES'
  | 'AUTHORITY_DUTY'
  | 'PROHIBITION_RESTRICTION'
  | 'PROCEDURE'
  | 'LIABILITY'
  | 'OTHER'

export type LawCategory = '民事' | '刑事' | '行政' | '商事经济' | '劳动社保' | '其他'

export interface OverallDraftValues {
  lawCategory: LawCategory | null
  overallKeywords: string | null
  summary: string | null
  overallNote: string | null
}

export interface ArticleDraftValues {
  itemType: ItemType | null
  keywords: string | null
  subjects: string | null
  legalLiability: string | null
  annotationNote: string | null
}

export interface EditableScope {
  overallEditable: boolean
  editableArticleIds: string[]
}

export interface AnnotationProgress {
  totalArticles: number
  filledArticles: number
  overallCompleted: boolean
}

export interface ReviewIssueFeedback {
  reviewRoundId: string
  locator: ReviewItemLocator
  reason: string
}

export interface TaskDraftResponse {
  taskId: string
  taskState: TaskState
  overallDraft: OverallDraftValues | null
  articleDrafts: Record<string, ArticleDraftValues>
  editableScope: EditableScope
  /** TaskDraftDocument 的草稿版本计数器，与修订任务或 revisionScope 无关。 */
  revision: number
  updatedAt: string | null
  progress: AnnotationProgress
  reviewIssues: ReviewIssueFeedback[]
}

export interface SaveOverallDraftPayload {
  lawCategory: LawCategory | null
  overallKeywords: string | null
  summary: string | null
  overallNote: string | null
}

export interface SaveArticleDraftPayload {
  itemType: ItemType | null
  keywords: string | null
  subjects: string | null
  legalLiability: string | null
  annotationNote: string | null
}

export interface SubmitReviewResult {
  taskId: string
  submissionId: string
  taskState: TaskState
  submittedAt: string
}

export interface OverallDraftForm {
  lawCategory: string
  overallKeywords: string
  summary: string
  overallNote: string
}

export interface ArticleDraftForm {
  itemType: string
  keywords: string
  subjects: string
  legalLiability: string
  annotationNote: string
}

export type AnnotationTarget =
  | { kind: 'overall' }
  | { kind: 'article'; articleId: string }

export interface ParsedAnnotationLocator {
  target: AnnotationTarget
  fieldKey: string
}

export type AnnotationSearchScope = 'ALL' | 'CONTENT' | 'ANNOTATION'

export interface HighlightSegment {
  text: string
  highlighted: boolean
}

export interface AnnotationSearchResult {
  key: string
  lawName: string
  target: AnnotationTarget
  articleId: string | null
  articleNumber: string | null
  structurePath: string
  fieldKey: string
  fieldLabel: string
  displayText: string
  snippet: string
  segments: HighlightSegment[]
  matchStart: number
  articleOrder: number
}

export interface AnnotationSearchPage {
  active: boolean
  query: string
  error: string | null
  items: AnnotationSearchResult[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface SubmissionLocator extends ErrorLocator {
  parsed: ParsedAnnotationLocator | null
}

export const LAW_CATEGORY_OPTIONS: readonly LawCategory[] = [
  '民事', '刑事', '行政', '商事经济', '劳动社保', '其他',
]

export const ITEM_TYPE_LABELS: Record<ItemType, string> = {
  DEFINITION: '定义解释类',
  RIGHTS_DUTIES: '权利义务类',
  AUTHORITY_DUTY: '授权职责类',
  PROHIBITION_RESTRICTION: '禁止限制类',
  PROCEDURE: '程序规则类',
  LIABILITY: '法律责任类',
  OTHER: '其他',
}

export const SEARCH_SCOPE_LABELS: Record<AnnotationSearchScope, string> = {
  ALL: '全部',
  CONTENT: '法律正文',
  ANNOTATION: '标注结果',
}

export const OVERALL_FIELD_LABELS: Record<keyof OverallDraftForm, string> = {
  lawCategory: '法律类别',
  overallKeywords: '整体关键词',
  summary: '摘要',
  overallNote: '备注',
}

export const ARTICLE_FIELD_LABELS: Record<keyof ArticleDraftForm, string> = {
  itemType: '条目类型',
  keywords: '关键词',
  subjects: '涉及主体',
  legalLiability: '法律责任',
  annotationNote: '标注备注',
}
