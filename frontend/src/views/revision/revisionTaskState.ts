import type { LawArticle, LawDetail, LawDisplayStatus, LawStructureNode } from '../../types/law'
import type { User } from '../../api/types'
import type {
  CreateRevisionTaskPayload,
  RevisionMode,
  TaskDetail,
} from '../../types/task'

export type RevisionCandidateKind = RevisionMode

export interface RevisionTaskFormInput {
  candidateKind: RevisionCandidateKind
  lawId: string
  annotatorId: string
  taskName: string
  remark: string
  overall: boolean
  articleIds: string[]
}

export interface RevisionAnnotatorState {
  annotators: User[]
  annotatorId: string
  annotatorsError: string
}

export function resetRevisionAnnotatorState(): RevisionAnnotatorState {
  return { annotators: [], annotatorId: '', annotatorsError: '' }
}

export function loadedRevisionAnnotatorState(
  users: User[],
  currentAnnotatorId: string,
  preserveSelection: boolean,
): RevisionAnnotatorState {
  const annotators = users.filter((user) => user.role === 'ANNOTATOR' && user.enabled)
  const annotatorId = preserveSelection
    && annotators.some((user) => user.id === currentAnnotatorId)
    ? currentAnnotatorId
    : ''
  return { annotators, annotatorId, annotatorsError: '' }
}

export function failedRevisionAnnotatorState(error: string): RevisionAnnotatorState {
  return { annotators: [], annotatorId: '', annotatorsError: error }
}

export function revisionCandidateKind(
  displayStatus: LawDisplayStatus,
): RevisionCandidateKind | null {
  if (displayStatus === 'COMPLETED') return 'ANNOTATION_ONLY'
  if (displayStatus === 'PENDING_REVISION') return 'CONTENT_CHANGE'
  return null
}

export function orderedRevisionArticles(
  detail: Pick<LawDetail, 'structure' | 'articles'>,
): LawArticle[] {
  const articleById = new Map(detail.articles.map((article) => [article.articleId, article]))
  const children = new Map<string | null, LawStructureNode[]>()
  for (const node of detail.structure) {
    const siblings = children.get(node.parentNodeId) ?? []
    siblings.push(node)
    children.set(node.parentNodeId, siblings)
  }
  const sortedNodes = (nodes: LawStructureNode[]) => [...nodes]
    .sort((left, right) => left.order - right.order)
  const ordered: LawArticle[] = []
  const included = new Set<string>()
  const visit = (node: LawStructureNode): void => {
    for (const articleId of node.articleIds) {
      const article = articleById.get(articleId)
      if (!article || included.has(articleId)) continue
      included.add(articleId)
      ordered.push(article)
    }
    for (const child of sortedNodes(children.get(node.nodeId) ?? [])) visit(child)
  }
  for (const root of sortedNodes(children.get(null) ?? [])) visit(root)
  for (const article of [...detail.articles].sort((left, right) => left.order - right.order)) {
    if (!included.has(article.articleId)) ordered.push(article)
  }
  return ordered
}

export function validateRevisionScope(
  candidateKind: RevisionCandidateKind,
  overall: boolean,
  articleIds: string[],
): string | null {
  if (candidateKind === 'ANNOTATION_ONLY' && !overall && articleIds.length === 0) {
    return '至少选择整体信息或一个法条'
  }
  return null
}

export function buildRevisionTaskPayload(
  form: RevisionTaskFormInput,
): CreateRevisionTaskPayload {
  const taskName = form.taskName.trim()
  const remark = form.remark.trim()
  return {
    lawId: form.lawId,
    annotatorId: form.annotatorId,
    ...(taskName ? { taskName } : {}),
    ...(remark ? { remark } : {}),
    overall: form.overall,
    articleIds: form.candidateKind === 'CONTENT_CHANGE'
      ? []
      : [...new Set(form.articleIds)],
  }
}

export interface RevisionScopeArticleDisplay {
  articleId: string
  label: string
  mandatory: boolean
}

export function revisionScopeArticles(
  task: Pick<TaskDetail, 'contentVersionSnapshot' | 'revisionScope'>,
): RevisionScopeArticleDisplay[] {
  if (!task.revisionScope) return []
  const labels = new Map(task.contentVersionSnapshot.articles
    .map((article) => [article.articleId, article.number]))
  const mandatory = new Set(task.revisionScope.mandatoryArticleIds)
  return task.revisionScope.articleIds.map((articleId) => ({
    articleId,
    label: labels.get(articleId) ?? articleId,
    mandatory: mandatory.has(articleId),
  }))
}
