import type {
  ArticleDraftForm,
  ArticleDraftValues,
  AnnotationTarget,
  OverallDraftForm,
  OverallDraftValues,
  ParsedAnnotationLocator,
  TaskDraftResponse,
} from '../../types/annotation'
import type { TaskDetail, TaskFieldConfigSnapshotItem } from '../../types/task'

export function createOverallForm(values: OverallDraftValues | null): OverallDraftForm {
  return {
    lawCategory: values?.lawCategory ?? '',
    overallKeywords: values?.overallKeywords ?? '',
    summary: values?.summary ?? '',
    overallNote: values?.overallNote ?? '',
  }
}

export function createArticleForm(values: ArticleDraftValues | null): ArticleDraftForm {
  return {
    itemType: values?.itemType ?? '',
    keywords: values?.keywords ?? '',
    subjects: values?.subjects ?? '',
    legalLiability: values?.legalLiability ?? '',
    annotationNote: values?.annotationNote ?? '',
  }
}

export function formsEqual<T extends Record<string, string>>(baseline: T, current: T): boolean {
  const baselineKeys = Object.keys(baseline)
  const currentKeys = Object.keys(current)
  return baselineKeys.length === currentKeys.length
    && baselineKeys.every((key) => baseline[key] === current[key])
}

export function reconcileSavedForm<T extends Record<string, string>>(
  serverSaved: T,
  submitted: T,
  current: T,
): { baseline: T; current: T; changedAfterRequest: boolean } {
  const changedAfterRequest = !formsEqual(submitted, current)
  return {
    baseline: serverSaved,
    current: changedAfterRequest ? current : serverSaved,
    changedAfterRequest,
  }
}

export interface WorkbenchSessionIdentity {
  taskId: string
  generation: number
}

export function sameWorkbenchSession(
  operation: WorkbenchSessionIdentity,
  current: WorkbenchSessionIdentity,
): boolean {
  return operation.taskId === current.taskId && operation.generation === current.generation
}

function hasText(value: string | null | undefined): boolean {
  return Boolean(value?.trim())
}

export function isArticleDraftComplete(
  config: TaskFieldConfigSnapshotItem[],
  values: ArticleDraftValues | null | undefined,
): boolean {
  if (!values) return false
  return config.filter((item) => item.required).every((item) => {
    switch (item.fieldKey) {
      case 'itemType': return values.itemType !== null
      case 'keywords': return hasText(values.keywords)
      case 'subjects': return hasText(values.subjects)
      case 'legalLiability': return hasText(values.legalLiability)
      case 'annotationNote': return hasText(values.annotationNote)
      default: return false
    }
  })
}

export function selectInitialTarget(
  task: Pick<TaskDetail, 'taskState' | 'contentVersionSnapshot' | 'fieldConfigSnapshot'>,
  draft: Pick<TaskDraftResponse, 'articleDrafts' | 'progress' | 'revision' | 'updatedAt'>,
): AnnotationTarget {
  const hasServerDraft = draft.revision > 0 || draft.updatedAt !== null
  if (task.taskState !== 'ANNOTATING' || !hasServerDraft) {
    return { kind: 'overall' }
  }

  const firstIncomplete = [...task.contentVersionSnapshot.articles]
    .sort((left, right) => left.order - right.order)
    .find((article) => !isArticleDraftComplete(
      task.fieldConfigSnapshot.article,
      draft.articleDrafts[article.articleId],
    ))

  return firstIncomplete
    ? { kind: 'article', articleId: firstIncomplete.articleId }
    : { kind: 'overall' }
}

export function parseAnnotationLocator(path: string): ParsedAnnotationLocator | null {
  const overallMatch = /^overall\.([A-Za-z][A-Za-z0-9]*)$/.exec(path)
  if (overallMatch) {
    return { target: { kind: 'overall' }, fieldKey: overallMatch[1] }
  }
  const articleMatch = /^articles\.([^.]+)\.([A-Za-z][A-Za-z0-9]*)$/.exec(path)
  if (articleMatch) {
    return {
      target: { kind: 'article', articleId: articleMatch[1] },
      fieldKey: articleMatch[2],
    }
  }
  return null
}

export function targetKey(target: AnnotationTarget): string {
  return target.kind === 'overall' ? 'overall' : `article:${target.articleId}`
}

export function sameTarget(left: AnnotationTarget, right: AnnotationTarget): boolean {
  return targetKey(left) === targetKey(right)
}

export type AnnotationNavigationIntent =
  | { kind: 'target'; target: AnnotationTarget; focusFieldKey?: string }
  | { kind: 'route'; path: string }

export function decideAnnotationNavigation(
  current: AnnotationTarget,
  dirty: boolean,
  intent: AnnotationNavigationIntent,
): 'apply' | 'confirm' {
  if (intent.kind === 'target' && sameTarget(current, intent.target)) return 'apply'
  return dirty ? 'confirm' : 'apply'
}

export function isTargetEditable(target: AnnotationTarget, draft: TaskDraftResponse): boolean {
  return target.kind === 'overall'
    ? draft.editableScope.overallEditable
    : draft.editableScope.editableArticleIds.includes(target.articleId)
}

export function isFieldRequired(
  config: TaskFieldConfigSnapshotItem[],
  fieldKey: string,
): boolean {
  return config.some((item) => item.fieldKey === fieldKey && item.required)
}
