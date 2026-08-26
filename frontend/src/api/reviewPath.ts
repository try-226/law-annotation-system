import type { ReviewTarget } from '../types/review'

function encodePath(value: string): string {
  return encodeURIComponent(value)
}

export function reviewBasePath(taskId: string): string {
  return `/tasks/${encodePath(taskId)}/review`
}

export function reviewRoundPath(taskId: string, roundId: string): string {
  return `${reviewBasePath(taskId)}/rounds/${encodePath(roundId)}`
}

export function reviewItemPath(taskId: string, roundId: string, target: ReviewTarget): string {
  const base = reviewRoundPath(taskId, roundId)
  return target.kind === 'overall'
    ? `${base}/overall`
    : `${base}/articles/${encodePath(target.articleId)}`
}
