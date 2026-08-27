import type {
  ArticleDraftForm,
  ArticleDraftValues,
  AnnotationTarget,
  OverallDraftForm,
  OverallDraftValues,
  ParsedAnnotationLocator,
  TaskDraftResponse,
} from '../../types/annotation'
import type {
  TaskArticleSnapshot,
  TaskDetail,
  TaskFieldConfigSnapshotItem,
  TaskStructureNodeSnapshot,
} from '../../types/task'
import type { ReviewItemLocator } from '../../types/review'

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
  task: Pick<TaskDetail, 'taskType' | 'taskState' | 'contentVersionSnapshot' | 'fieldConfigSnapshot' | 'structureSnapshot'>,
  draft: Pick<TaskDraftResponse, 'articleDrafts' | 'progress' | 'editableScope' | 'revision' | 'updatedAt'>,
): AnnotationTarget {
  if (task.taskType === 'REVISION') {
    if (draft.editableScope.overallEditable) return { kind: 'overall' }
    const editable = new Set(draft.editableScope.editableArticleIds)
    const firstEditable = orderedTaskArticles(task)
      .find((article) => editable.has(article.articleId))
    return firstEditable
      ? { kind: 'article', articleId: firstEditable.articleId }
      : { kind: 'overall' }
  }

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

export function orderedTaskArticles(
  task: Pick<TaskDetail, 'contentVersionSnapshot' | 'structureSnapshot'>,
): TaskDetail['contentVersionSnapshot']['articles'] {
  return orderedTaskStructureRows(task)
    .filter((row): row is Extract<AnnotationStructureRow, { kind: 'article' }> => row.kind === 'article')
    .map((row) => row.article)
}

export type AnnotationStructureRow =
  | { kind: 'node'; key: string; node: TaskStructureNodeSnapshot; depth: number }
  | { kind: 'article'; key: string; article: TaskArticleSnapshot; depth: number }

export function orderedTaskStructureRows(
  task: Pick<TaskDetail, 'contentVersionSnapshot' | 'structureSnapshot'>,
): AnnotationStructureRow[] {
  const articles = task.contentVersionSnapshot.articles
  const articleById = new Map(articles.map((article) => [article.articleId, article]))
  const children = new Map<string | null, TaskDetail['structureSnapshot']>()
  for (const node of task.structureSnapshot) {
    const siblings = children.get(node.parentNodeId) ?? []
    siblings.push(node)
    children.set(node.parentNodeId, siblings)
  }
  const sortedNodes = (nodes: TaskDetail['structureSnapshot']) => [...nodes]
    .sort((left, right) => left.order - right.order)
  const ordered: AnnotationStructureRow[] = []
  const included = new Set<string>()
  const visit = (node: TaskDetail['structureSnapshot'][number], depth: number): void => {
    ordered.push({ kind: 'node', key: `node:${node.nodeId}`, node, depth })
    for (const articleId of node.articleIds) {
      const article = articleById.get(articleId)
      if (!article || included.has(articleId)) continue
      included.add(articleId)
      ordered.push({ kind: 'article', key: `article:${articleId}`, article, depth: depth + 1 })
    }
    for (const child of sortedNodes(children.get(node.nodeId) ?? [])) visit(child, depth + 1)
  }
  for (const root of sortedNodes(children.get(null) ?? [])) visit(root, 0)
  for (const article of [...articles].sort((left, right) => left.order - right.order)) {
    if (!included.has(article.articleId)) {
      ordered.push({
        kind: 'article', key: `article:${article.articleId}`, article, depth: 0,
      })
    }
  }
  return ordered
}

export function parseAnnotationLocator(path: string): ParsedAnnotationLocator | null {
  if (path === 'overall') return { target: { kind: 'overall' }, fieldKey: '' }
  const overallMatch = /^overall\.([A-Za-z][A-Za-z0-9]*)$/.exec(path)
  if (overallMatch) {
    return { target: { kind: 'overall' }, fieldKey: overallMatch[1] }
  }
  const articleScopeMatch = /^articles\.([^.]+)$/.exec(path)
  if (articleScopeMatch) {
    return {
      target: { kind: 'article', articleId: articleScopeMatch[1] },
      fieldKey: '',
    }
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

export function reviewIssueTarget(locator: ReviewItemLocator): AnnotationTarget | null {
  if (locator.type === 'OVERALL') return { kind: 'overall' }
  return locator.articleId ? { kind: 'article', articleId: locator.articleId } : null
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

export function canEditAnnotationTarget(
  task: Pick<TaskDetail, 'taskType' | 'taskState' | 'revisionScope'>,
  target: AnnotationTarget,
  draft: Pick<TaskDraftResponse, 'editableScope'>,
): boolean {
  const stateAllowsEditing = task.taskType === 'REVISION'
    ? task.revisionScope !== null
      && (task.taskState === 'ANNOTATING' || task.taskState === 'PARTIALLY_REJECTED')
    : task.taskState === 'ANNOTATING'
  if (!stateAllowsEditing) return false
  return target.kind === 'overall'
    ? draft.editableScope.overallEditable
    : draft.editableScope.editableArticleIds.includes(target.articleId)
}

export type AnnotationSubmissionAction = 'review' | 'rereview'

export function annotationSubmissionAction(
  task: Pick<TaskDetail, 'taskType' | 'taskState' | 'revisionScope'>,
  draft: Pick<TaskDraftResponse, 'editableScope'>,
): AnnotationSubmissionAction | null {
  if (task.taskType === 'REVISION') {
    if (task.revisionScope === null) return null
    if (task.taskState === 'ANNOTATING') return 'review'
    if (task.taskState === 'PARTIALLY_REJECTED') return 'rereview'
    return null
  }
  return task.taskState === 'ANNOTATING' && draft.editableScope.overallEditable
    ? 'review'
    : null
}

export function isFieldRequired(
  config: TaskFieldConfigSnapshotItem[],
  fieldKey: string,
): boolean {
  return config.some((item) => item.fieldKey === fieldKey && item.required)
}
