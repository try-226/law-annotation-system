import type { RouteLocationRaw } from 'vue-router'

import type { TaskArticleSnapshot } from '../../types/task'
import type {
  AnnotationVersionArticleResult,
  HistoryDetailRef,
  HistoryTimelineItem,
} from '../../types/history'

export interface AnnotationArticleRow extends TaskArticleSnapshot {
  values: AnnotationVersionArticleResult['values']
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
