import {
  ARTICLE_FIELD_LABELS,
  ITEM_TYPE_LABELS,
  OVERALL_FIELD_LABELS,
  type ArticleDraftValues,
  type OverallDraftValues,
} from '../../types/annotation'
import type { TaskArticleSnapshot } from '../../types/task'
import type {
  ReviewDetail,
  ReviewFieldRow,
  ReviewItem,
  ReviewItemLocator,
  ReviewTarget,
  ReviewTargetCapabilities,
} from '../../types/review'

const OVERALL_FIELDS: readonly (keyof OverallDraftValues)[] = [
  'lawCategory', 'overallKeywords', 'summary', 'overallNote',
]

const ARTICLE_FIELDS: readonly (keyof ArticleDraftValues)[] = [
  'itemType', 'keywords', 'subjects', 'legalLiability', 'annotationNote',
]

export function reviewTargetKey(target: ReviewTarget): string {
  return target.kind === 'overall' ? 'overall' : `article:${target.articleId}`
}

export function locatorTarget(locator: ReviewItemLocator): ReviewTarget {
  return locator.type === 'OVERALL'
    ? { kind: 'overall' }
    : { kind: 'article', articleId: locator.articleId ?? '' }
}

export function buildReviewItemMap(items: ReviewItem[]): Map<string, ReviewItem> {
  return new Map(items.map((item) => [reviewTargetKey(locatorTarget(item.locator)), item]))
}

export function buildReviewTargetOrder(
  review: Pick<ReviewDetail, 'contentVersionSnapshot'>,
): ReviewTarget[] {
  return [
    { kind: 'overall' },
    ...[...review.contentVersionSnapshot.articles]
      .sort((left, right) => left.order - right.order)
      .map((article) => ({ kind: 'article' as const, articleId: article.articleId })),
  ]
}

export function selectInitialReviewTarget(
  review: Pick<ReviewDetail, 'contentVersionSnapshot' | 'items'>,
): ReviewTarget {
  const order = buildReviewTargetOrder(review)
  const items = buildReviewItemMap(review.items)
  return order.find((target) => items.get(reviewTargetKey(target))?.state === 'UNREVIEWED')
    ?? order.find((target) => items.get(reviewTargetKey(target))?.state === 'NEEDS_CHANGE')
    ?? order.find((target) => items.has(reviewTargetKey(target)))
    ?? order[0]
    ?? { kind: 'overall' }
}

export function findNextUnreviewedTarget(
  review: Pick<ReviewDetail, 'contentVersionSnapshot' | 'items'>,
  current: ReviewTarget,
): ReviewTarget | null {
  const order = buildReviewTargetOrder(review)
  if (order.length < 2) return null
  const items = buildReviewItemMap(review.items)
  const currentIndex = order.findIndex((target) => reviewTargetKey(target) === reviewTargetKey(current))
  const start = currentIndex < 0 ? -1 : currentIndex
  for (let offset = 1; offset < order.length; offset += 1) {
    const target = order[(start + offset + order.length) % order.length]
    if (items.get(reviewTargetKey(target))?.state === 'UNREVIEWED') return target
  }
  return null
}

export function findNextReviewTarget(
  review: Pick<ReviewDetail, 'contentVersionSnapshot'>,
  current: ReviewTarget,
): ReviewTarget | null {
  const order = buildReviewTargetOrder(review)
  const currentIndex = order.findIndex((target) => reviewTargetKey(target) === reviewTargetKey(current))
  return currentIndex >= 0 && currentIndex + 1 < order.length ? order[currentIndex + 1] : null
}

export function reviewTargetCapabilities(
  review: Pick<ReviewDetail, 'roundType' | 'writable' | 'items' | 'completionStartedAt' | 'completedAt'>,
  target: ReviewTarget,
): ReviewTargetCapabilities {
  const item = buildReviewItemMap(review.items).get(reviewTargetKey(target))
  const active = review.writable && !review.completionStartedAt && !review.completedAt
  const inScope = Boolean(item)
  const canCheck = active && inScope && item?.state === 'UNREVIEWED'
  return {
    inScope,
    state: item?.state ?? null,
    canCheck,
    canIssue: active && (inScope || review.roundType === 'REREVIEW'),
    canCheckAndNext: canCheck && item?.state === 'UNREVIEWED',
  }
}

export function canCompleteReview(
  review: Pick<ReviewDetail, 'writable' | 'progress' | 'completionStartedAt' | 'completedAt'>,
): boolean {
  return review.writable
    && !review.completionStartedAt
    && !review.completedAt
    && review.progress.unreviewed === 0
}

export function validateIssueReason(reason: string): string | null {
  const value = reason.trim()
  if (!value) return '问题原因不能为空'
  if (/\p{Cc}/u.test(value)) return '问题原因不得包含控制字符'
  if (Array.from(value).length > 500) return '问题原因不能超过500个字符'
  return null
}

export function normalizeIssueReason(reason: string): string {
  return reason.trim()
}

export type ReviewFailureDecision = 'not-started' | 'reason' | 'reload' | 'fatal'

export function reviewFailureDecision(code: string | undefined): ReviewFailureDecision {
  if (code === 'REVIEW.NOT_STARTED') return 'not-started'
  if (code === 'REVIEW.ISSUE_REASON_INVALID') return 'reason'
  if ([
    'REVIEW.ALREADY_ASSIGNED',
    'REVIEW.NOT_REVIEWER',
    'REVIEW.INVALID_TASK_STATE',
    'REVIEW.ITEM_NOT_IN_SCOPE',
    'REVIEW.INCOMPLETE',
    'REVIEW.ALREADY_COMPLETED',
    'REVIEW.COMPLETION_CONFLICT',
  ].includes(code ?? '')) return 'reload'
  return 'fatal'
}

function displayValue(value: string | null | undefined): string {
  const text = value?.trim()
  return text || '—'
}

function overallValue(values: OverallDraftValues | null, field: keyof OverallDraftValues): string {
  return displayValue(values?.[field])
}

function articleValue(values: ArticleDraftValues | null, field: keyof ArticleDraftValues): string {
  if (field === 'itemType') {
    return values?.itemType ? ITEM_TYPE_LABELS[values.itemType] : '—'
  }
  return displayValue(values?.[field])
}

export function buildReviewFieldRows(
  kind: 'overall',
  before: OverallDraftValues | null,
  after: OverallDraftValues | null,
): ReviewFieldRow[]
export function buildReviewFieldRows(
  kind: 'article',
  before: ArticleDraftValues | null,
  after: ArticleDraftValues | null,
): ReviewFieldRow[]
export function buildReviewFieldRows(
  kind: 'overall' | 'article',
  before: OverallDraftValues | ArticleDraftValues | null,
  after: OverallDraftValues | ArticleDraftValues | null,
): ReviewFieldRow[] {
  if (kind === 'overall') {
    const previous = before as OverallDraftValues | null
    const current = after as OverallDraftValues | null
    return OVERALL_FIELDS.map((key) => {
      const previousValue = overallValue(previous, key)
      const currentValue = overallValue(current, key)
      return { key, label: OVERALL_FIELD_LABELS[key], before: previousValue, after: currentValue, changed: previousValue !== currentValue }
    })
  }
  const previous = before as ArticleDraftValues | null
  const current = after as ArticleDraftValues | null
  return ARTICLE_FIELDS.map((key) => {
    const previousValue = articleValue(previous, key)
    const currentValue = articleValue(current, key)
    return { key, label: ARTICLE_FIELD_LABELS[key], before: previousValue, after: currentValue, changed: previousValue !== currentValue }
  })
}

export function searchReviewArticles(
  articles: TaskArticleSnapshot[],
  query: string,
): TaskArticleSnapshot[] {
  const keyword = query.trim().toLocaleLowerCase('zh-CN')
  if (!keyword) return []
  return [...articles]
    .sort((left, right) => left.order - right.order)
    .filter((article) => `${article.number}\n${article.body}`.toLocaleLowerCase('zh-CN').includes(keyword))
}
