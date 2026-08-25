import {
  ITEM_TYPE_LABELS,
  LAW_CATEGORY_OPTIONS,
  type ArticleDraftForm,
  type ItemType,
  type OverallDraftForm,
  type SaveArticleDraftPayload,
  type SaveOverallDraftPayload,
} from '../../types/annotation'

export type AnnotationFieldErrors = Record<string, string>

const CONTROL_PATTERN = /[\u0000-\u001f\u007f-\u009f]/u

function codePointLength(value: string): number {
  return [...value].length
}

function normalizedText(value: string): string | null {
  const normalized = value.trim()
  return normalized ? normalized : null
}

function validateText(value: string, maxLength: number): string | null {
  const normalized = normalizedText(value)
  if (!normalized) return null
  if (CONTROL_PATTERN.test(normalized)) {
    return `长度不能超过${maxLength}个字符且不得包含控制字符`
  }
  if (codePointLength(normalized) > maxLength) {
    return `长度不能超过${maxLength}个字符且不得包含控制字符`
  }
  return null
}

function normalizeKeywords(value: string): string | null {
  const normalized = normalizedText(value)
  if (!normalized) return null
  return normalized.split(/[,，]/u).map((part) => part.trim()).join(',')
}

function validateKeywords(value: string): string | null {
  const textError = validateText(value, 200)
  if (textError) return textError
  const normalized = normalizedText(value)
  if (!normalized) return null
  const parts = normalized.split(/[,，]/u)
  if (parts.length > 20) return '关键词最多20个'
  if (parts.some((part) => {
    const length = codePointLength(part.trim())
    return length < 1 || length > 30
  })) {
    return '单个关键词须为1至30个字符且不能为空'
  }
  const joined = normalizeKeywords(value)
  return joined && codePointLength(joined) > 200 ? '关键词总长度不能超过200个字符' : null
}

function addError(errors: AnnotationFieldErrors, key: string, error: string | null): void {
  if (error) errors[key] = error
}

export function validateOverallDraft(form: OverallDraftForm): AnnotationFieldErrors {
  const errors: AnnotationFieldErrors = {}
  if (form.lawCategory && !LAW_CATEGORY_OPTIONS.includes(form.lawCategory.trim() as never)) {
    errors.lawCategory = '值不在允许范围内'
  }
  addError(errors, 'lawCategory', validateText(form.lawCategory, 100))
  addError(errors, 'overallKeywords', validateKeywords(form.overallKeywords))
  addError(errors, 'summary', validateText(form.summary, 2000))
  addError(errors, 'overallNote', validateText(form.overallNote, 1000))
  return errors
}

export function validateArticleDraft(form: ArticleDraftForm): AnnotationFieldErrors {
  const errors: AnnotationFieldErrors = {}
  if (form.itemType && !(form.itemType.trim() in ITEM_TYPE_LABELS)) {
    errors.itemType = '值不在允许范围内'
  }
  addError(errors, 'itemType', validateText(form.itemType, 100))
  addError(errors, 'keywords', validateKeywords(form.keywords))
  addError(errors, 'subjects', validateText(form.subjects, 200))
  addError(errors, 'legalLiability', validateText(form.legalLiability, 1000))
  addError(errors, 'annotationNote', validateText(form.annotationNote, 1000))
  return errors
}

export function normalizeOverallPayload(form: OverallDraftForm): SaveOverallDraftPayload {
  return {
    lawCategory: normalizedText(form.lawCategory) as SaveOverallDraftPayload['lawCategory'],
    overallKeywords: normalizeKeywords(form.overallKeywords),
    summary: normalizedText(form.summary),
    overallNote: normalizedText(form.overallNote),
  }
}

export function normalizeArticlePayload(form: ArticleDraftForm): SaveArticleDraftPayload {
  return {
    itemType: normalizedText(form.itemType) as ItemType | null,
    keywords: normalizeKeywords(form.keywords),
    subjects: normalizedText(form.subjects),
    legalLiability: normalizedText(form.legalLiability),
    annotationNote: normalizedText(form.annotationNote),
  }
}
