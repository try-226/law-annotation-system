import type { RouteLocationRaw } from 'vue-router'

import type { Role } from '../../api/types'
import { ARTICLE_FIELD_LABELS, OVERALL_FIELD_LABELS } from '../../types/annotation'
import { VALIDITY_STATUS_LABELS } from '../../types/law'
import type { TaskArticleSnapshot } from '../../types/task'
import type {
  AnnotationVersionArticleResult,
  HistoryDetailType,
  HistoryDetailRef,
  HistoryTimelineItem,
} from '../../types/history'

export interface AnnotationArticleRow extends TaskArticleSnapshot {
  values: AnnotationVersionArticleResult['values']
}

export function taskHistoryRoute(lawId: string, taskId: string): RouteLocationRaw {
  return {
    name: 'task-history',
    params: { lawId, taskId },
  }
}

export function historyDetailRoute(lawId: string, detailRef: HistoryDetailRef): RouteLocationRaw | null {
  switch (detailRef.type) {
    case 'CONTENT_VERSION':
      return {
        name: 'history-content-version',
        params: { lawId, contentVersionId: detailRef.resourceId },
      }
    case 'ANNOTATION_VERSION':
      return {
        name: 'history-annotation-version',
        params: { lawId, annotationVersionId: detailRef.resourceId },
      }
    case 'LAW_AUDIT':
      return {
        name: 'history-law-audit',
        params: { lawId, auditId: detailRef.resourceId },
      }
    case 'TASK':
      return {
        name: 'task-history',
        params: { lawId, taskId: detailRef.resourceId },
        query: { from: 'law-history' },
      }
    default:
      return null
  }
}

export function historyTimelineRows(lawId: string, timeline: HistoryTimelineItem[]) {
  return timeline.map((item) => ({
    ...item,
    route: historyDetailRoute(lawId, item.detailRef),
  }))
}

export interface TimelinePage<T> {
  items: T[]
  page: number
  totalPages: number
  totalItems: number
}

export function paginateTimeline<T>(items: T[], requestedPage: number, pageSize: number): TimelinePage<T> {
  const safePageSize = Math.max(1, Math.floor(pageSize))
  const totalPages = Math.ceil(items.length / safePageSize)
  const page = Math.min(Math.max(1, Math.floor(requestedPage)), Math.max(1, totalPages))
  const start = (page - 1) * safePageSize
  return {
    items: items.slice(start, start + safePageSize),
    page,
    totalPages,
    totalItems: items.length,
  }
}

export function taskHistoryBackRoute(
  role: Role | undefined,
  lawId: string,
  taskId: string,
  from: unknown,
): RouteLocationRaw {
  if (isLawHistoryReturnContext(role, from)) {
    return { name: 'law-history', params: { lawId } }
  }
  return {
    name: role === 'ADMIN' ? 'admin-task-detail' : 'my-task-detail',
    params: { taskId },
  }
}

export function isLawHistoryReturnContext(role: Role | undefined, from: unknown): boolean {
  return role === 'ADMIN' && from === 'law-history'
}

export interface HistoryBackNavigationInput {
  kind: HistoryDetailType | null
  role: Role | undefined
  lawId: string
  taskId: string
  from: unknown
  loadError: boolean
}

export function historyBackNavigation(input: HistoryBackNavigationInput): {
  route: RouteLocationRaw
  label: string
} {
  if (input.kind !== 'TASK') {
    return {
      route: { name: 'law-history', params: { lawId: input.lawId } },
      label: '历史记录',
    }
  }

  const returnsToLawHistory = isLawHistoryReturnContext(input.role, input.from)
  return {
    route: taskHistoryBackRoute(input.role, input.lawId, input.taskId, input.from),
    label: returnsToLawHistory ? '法律历史' : '任务详情',
  }
}

export function annotationArticleRows(
  results: AnnotationVersionArticleResult[],
  articles: TaskArticleSnapshot[],
): AnnotationArticleRow[] {
  const resultsByArticleId = new Map(results.map((result) => [result.articleId, result.values]))
  return articles.flatMap((article) => {
    const values = resultsByArticleId.get(article.articleId)
    return values ? [{ ...article, values }] : []
  })
}

export function formatAuditValue(value: unknown): string {
  try {
    const formatted = JSON.stringify(value, null, 2)
    return formatted === undefined ? String(value) : formatted
  } catch {
    return '[无法格式化的历史值]'
  }
}

function labelFromEntries(entries: ReadonlyArray<readonly [string, string]>, value: string): string {
  return entries.find(([key]) => key === value)?.[1] ?? value
}

export function validityStatusLabel(status: string): string {
  return labelFromEntries(Object.entries(VALIDITY_STATUS_LABELS), status)
}

export function historyFieldLabel(scope: 'overall' | 'article', fieldKey: string): string {
  const labels = scope === 'overall' ? OVERALL_FIELD_LABELS : ARTICLE_FIELD_LABELS
  return labelFromEntries(Object.entries(labels), fieldKey)
}

export function lawAuditTypeLabel(auditType: string): string {
  return labelFromEntries([
    ['BASE_INFO', '基础信息变更'],
    ['STRUCTURE', '结构变更'],
  ], auditType)
}
