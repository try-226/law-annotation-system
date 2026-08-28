import { parseFailure, safeErrorMessage } from '../../utils/errors'

export function exportFailureMessage(error: unknown): string {
  const failure = parseFailure(error)
  const messages: Record<string, string> = {
    'EXPORT.FORMAL_UNAVAILABLE': '该法律尚无正式标注结果，可改为导出纯法律正文。',
    'EXPORT.VERSION_MISMATCH': '当前正文与正式标注版本不匹配，请完成修订后重试。',
    'EXPORT.ANNOTATION_INCONSISTENT': '正式标注数据不完整或与当前法律不一致，请联系管理员检查。',
    'EXPORT.SELECTION_INVALID': '选中的法条无效，请刷新页面并重新选择。',
    'LAW.NOT_FOUND': '法律不存在、已删除或已进入回收站，无法导出。',
  }
  if (failure.code && messages[failure.code]) return messages[failure.code]
  return safeErrorMessage(error, '导出失败，请保留当前选择后重试')
}
