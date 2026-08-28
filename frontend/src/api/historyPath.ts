function segment(value: string): string {
  return encodeURIComponent(value)
}

export function lawHistoryPath(lawId: string): string {
  return `/laws/${segment(lawId)}/history`
}

export function contentVersionHistoryPath(lawId: string, contentVersionId: string): string {
  return `${lawHistoryPath(lawId)}/content-versions/${segment(contentVersionId)}`
}

export function annotationVersionHistoryPath(lawId: string, annotationVersionId: string): string {
  return `${lawHistoryPath(lawId)}/annotation-versions/${segment(annotationVersionId)}`
}

export function lawAuditHistoryPath(lawId: string, auditId: string): string {
  return `${lawHistoryPath(lawId)}/audits/${segment(auditId)}`
}

export function taskHistoryPath(lawId: string, taskId: string): string {
  return `${lawHistoryPath(lawId)}/tasks/${segment(taskId)}`
}
