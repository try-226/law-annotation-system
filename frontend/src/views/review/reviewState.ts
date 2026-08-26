import {
  ARTICLE_FIELD_LABELS,
  ITEM_TYPE_LABELS,
  OVERALL_FIELD_LABELS,
  type ArticleDraftValues,
  type OverallDraftValues,
} from '../../types/annotation'
import type { TaskArticleSnapshot, TaskStructureNodeSnapshot } from '../../types/task'
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

export type ReviewDirectoryRow =
  | { kind: 'node'; key: string; node: TaskStructureNodeSnapshot; depth: number }
  | { kind: 'article'; key: string; article: TaskArticleSnapshot; depth: number }

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

export function buildReviewDirectoryRows(
  review: Pick<ReviewDetail, 'contentVersionSnapshot' | 'structureSnapshot'>,
): ReviewDirectoryRow[] {
  const nodes = review.structureSnapshot
  const articleById = new Map(review.contentVersionSnapshot.articles.map((article) => [article.articleId, article]))
  const children = new Map<string | null, TaskStructureNodeSnapshot[]>()
  for (const node of nodes) {
    const list = children.get(node.parentNodeId) ?? []
    list.push(node)
    children.set(node.parentNodeId, list)
  }
  const result: ReviewDirectoryRow[] = []
  const included = new Set<string>()
  const sortedNodes = (items: TaskStructureNodeSnapshot[]) => [...items].sort((left, right) => left.order - right.order)
  const visit = (node: TaskStructureNodeSnapshot, depth: number) => {
    result.push({ kind: 'node', key: `node:${node.nodeId}`, node, depth })
    const nodeArticles = node.articleIds
      .map((articleId) => articleById.get(articleId))
      .filter((article): article is TaskArticleSnapshot => Boolean(article))
      .sort((left, right) => left.order - right.order)
    for (const article of nodeArticles) {
      if (included.has(article.articleId)) continue
      included.add(article.articleId)
      result.push({ kind: 'article', key: `article:${article.articleId}`, article, depth: depth + 1 })
    }
    for (const child of sortedNodes(children.get(node.nodeId) ?? [])) visit(child, depth + 1)
  }
  for (const root of sortedNodes(children.get(null) ?? [])) visit(root, 0)
  for (const article of [...review.contentVersionSnapshot.articles].sort((left, right) => left.order - right.order)) {
    if (!included.has(article.articleId)) result.push({ kind: 'article', key: `article:${article.articleId}`, article, depth: 0 })
  }
  return result
}

export function buildReviewTargetOrder(
  review: Pick<ReviewDetail, 'contentVersionSnapshot' | 'structureSnapshot'>,
): ReviewTarget[] {
  return [
    { kind: 'overall' },
    ...buildReviewDirectoryRows(review)
      .filter((row) => row.kind === 'article')
      .map((row) => ({ kind: 'article' as const, articleId: row.article.articleId })),
  ]
}

export function selectInitialReviewTarget(
  review: Pick<ReviewDetail, 'contentVersionSnapshot' | 'structureSnapshot' | 'items'>,
): ReviewTarget {
  const order = buildReviewTargetOrder(review)
  const items = buildReviewItemMap(review.items)
  return order.find((target) => items.get(reviewTargetKey(target))?.state === 'UNREVIEWED')
    ?? order.find((target) => items.get(reviewTargetKey(target))?.state === 'NEEDS_CHANGE')
    ?? order.find((target) => items.has(reviewTargetKey(target)))
    ?? order[0]
    ?? { kind: 'overall' }
}

export function findNextReviewTarget(
  review: Pick<ReviewDetail, 'contentVersionSnapshot' | 'structureSnapshot'>,
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
  const canCheck = active && inScope && item?.state !== 'CHECKED'
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

export function canResumeReviewCompletion(
  review: Pick<ReviewDetail, 'reviewerId' | 'completionStartedAt' | 'completedAt'>,
  currentUserId: string | null | undefined,
): boolean {
  return Boolean(
    currentUserId
    && review.reviewerId === currentUserId
    && review.completionStartedAt
    && !review.completedAt,
  )
}

export interface ReviewArticleProgress {
  total: number
  processed: number
  checked: number
  needsChange: number
  unreviewed: number
}

export function buildReviewArticleProgress(items: ReviewItem[]): ReviewArticleProgress {
  const progress: ReviewArticleProgress = {
    total: 0,
    processed: 0,
    checked: 0,
    needsChange: 0,
    unreviewed: 0,
  }
  for (const item of items) {
    if (item.locator.type !== 'ARTICLE') continue
    progress.total += 1
    if (item.state === 'CHECKED') progress.checked += 1
    else if (item.state === 'NEEDS_CHANGE') progress.needsChange += 1
    else progress.unreviewed += 1
  }
  progress.processed = progress.checked + progress.needsChange
  return progress
}

export function shouldCompareReviewTarget(
  review: Pick<ReviewDetail, 'roundType' | 'items'>,
  target: ReviewTarget,
): boolean {
  return review.roundType === 'REREVIEW'
    && buildReviewItemMap(review.items).has(reviewTargetKey(target))
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
