import type { AnnotationVersionHistory } from '../../types/history'
import type { LawDetail } from '../../types/law'
import type { ExportFormat, ExportScope, ExportType, LawExportRequest } from '../../types/export'

export class ExportSelectionError extends Error {}

export interface FormalAvailability {
  available: boolean
  message: string
}

export function buildLawExportRequest(
  scope: ExportScope,
  selectedArticleIds: string[],
  type: ExportType,
  format: ExportFormat,
): LawExportRequest {
  if (scope === 'WHOLE') return { scope, articleIds: [], type, format }
  if (selectedArticleIds.some((articleId) => !articleId)) {
    throw new ExportSelectionError('选中的法条标识无效，请刷新页面后重试')
  }
  const articleIds = [...new Set(selectedArticleIds)]
  if (articleIds.length === 0) {
    throw new ExportSelectionError('请选择至少一条法条后再导出')
  }
  return { scope, articleIds, type, format }
}

export function formalAvailability(
  law: Pick<LawDetail, 'id' | 'currentAnnotationVersionId' | 'currentContentVersionId' | 'pendingRevision'>,
  annotation: AnnotationVersionHistory | null,
  loadFailed = false,
): FormalAvailability {
  if (law.pendingRevision) {
    return {
      available: false,
      message: '当前法律存在待修订的正文变更，正式标注结果暂不可按当前正文导出，请完成修订后重试。',
    }
  }
  if (loadFailed) {
    return { available: false, message: '当前正式标注结果尚未成功加载，请稍后重试。' }
  }
  if (!law.currentAnnotationVersionId) {
    return { available: false, message: '该法律尚无正式标注结果，可先导出纯法律正文。' }
  }
  if (
    !annotation
    || annotation.lawId !== law.id
    || annotation.annotationVersionId !== law.currentAnnotationVersionId
  ) {
    return { available: false, message: '当前正式标注结果尚未成功加载，请稍后重试。' }
  }
  if (annotation.contentVersionId !== law.currentContentVersionId) {
    return {
      available: false,
      message: '法条语义内容已更新，当前正文与正式标注版本不匹配；请完成修订后再导出正式结果。',
    }
  }
  return {
    available: true,
    message: '当前正式 A 与语义 C 配对有效。',
  }
}

function safeFilename(filename: string, fallback: string): string {
  const sanitized = filename.trim().replace(/[\\/:*?"<>|\u0000-\u001f\u007f]/g, '_')
  return sanitized || fallback
}

export function filenameFromContentDisposition(header: unknown, fallback: string): string {
  if (typeof header !== 'string') return fallback
  const extended = /filename\*\s*=\s*(?:UTF-8'')?([^;]+)/i.exec(header)?.[1]
  if (extended) {
    const encoded = extended.trim().replace(/^"|"$/g, '')
    try {
      return safeFilename(decodeURIComponent(encoded), fallback)
    } catch {
      // Fall back to filename= when an invalid extended value is returned.
    }
  }
  const basic = /filename\s*=\s*(?:"([^"]+)"|([^;]+))/i.exec(header)
  const value = (basic?.[1] ?? basic?.[2])?.trim()
  return value ? safeFilename(value, fallback) : fallback
}

export function triggerBlobDownload(blob: Blob, filename: string): void {
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  try {
    link.href = objectUrl
    link.download = filename
    link.hidden = true
    document.body.appendChild(link)
    link.click()
  } finally {
    link.remove()
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 0)
  }
}
