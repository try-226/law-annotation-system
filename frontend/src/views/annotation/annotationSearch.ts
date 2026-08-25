import {
  ARTICLE_FIELD_LABELS,
  ITEM_TYPE_LABELS,
  OVERALL_FIELD_LABELS,
  type AnnotationSearchPage,
  type AnnotationSearchResult,
  type AnnotationSearchScope,
  type HighlightSegment,
  type TaskDraftResponse,
} from '../../types/annotation'
import type { TaskDetail, TaskStructureNodeSnapshot } from '../../types/task'

const CONTROL_PATTERN = /[\u0000-\u001f\u007f-\u009f]/u
const FIELD_PRIORITY = [
  'lawCategory', 'overallKeywords', 'summary', 'overallNote',
  'structureTitle', 'articleNumber', 'articleBody',
  'itemType', 'keywords', 'subjects', 'legalLiability', 'annotationNote',
]

interface SearchOptions {
  query: string
  scope: AnnotationSearchScope
  page?: number
  size?: number
}

interface Candidate {
  targetKind: 'overall' | 'article'
  articleId: string | null
  articleNumber: string | null
  articleOrder: number
  structurePath: string
  fieldKey: string
  fieldLabel: string
  text: string
  dedupeKey?: string
}

function literalMatches(text: string, query: string): number[] {
  const haystack = text.toLocaleLowerCase('zh-CN')
  const needle = query.toLocaleLowerCase('zh-CN')
  const matches: number[] = []
  let from = 0
  while (from <= haystack.length - needle.length) {
    const index = haystack.indexOf(needle, from)
    if (index < 0) break
    matches.push(index)
    from = index + Math.max(needle.length, 1)
  }
  return matches
}

function snippetFor(text: string, matchStart: number, queryLength: number): string {
  const start = Math.max(0, matchStart - 32)
  const end = Math.min(text.length, matchStart + queryLength + 48)
  return `${start > 0 ? '…' : ''}${text.slice(start, end)}${end < text.length ? '…' : ''}`
}

export function buildHighlightSegments(text: string, query: string): HighlightSegment[] {
  if (!query) return [{ text, highlighted: false }]
  const matches = literalMatches(text, query)
  if (!matches.length) return [{ text, highlighted: false }]
  const segments: HighlightSegment[] = []
  let cursor = 0
  for (const index of matches) {
    if (index > cursor) segments.push({ text: text.slice(cursor, index), highlighted: false })
    segments.push({ text: text.slice(index, index + query.length), highlighted: true })
    cursor = index + query.length
  }
  if (cursor < text.length) segments.push({ text: text.slice(cursor), highlighted: false })
  return segments
}

function structureTools(nodes: TaskStructureNodeSnapshot[]) {
  const indexed = nodes.map((node, index) => ({ node, index }))
  const byId = new Map(nodes.map((node) => [node.nodeId, node]))
  const children = new Map<string, TaskStructureNodeSnapshot[]>()
  for (const { node } of indexed) {
    if (!node.parentNodeId) continue
    const items = children.get(node.parentNodeId) ?? []
    items.push(node)
    children.set(node.parentNodeId, items)
  }
  const ordered = (items: TaskStructureNodeSnapshot[]) => [...items].sort((left, right) => {
    if (left.order !== right.order) return left.order - right.order
    return indexed.find((entry) => entry.node.nodeId === left.nodeId)!.index
      - indexed.find((entry) => entry.node.nodeId === right.nodeId)!.index
  })
  const descendantArticleIds = (root: TaskStructureNodeSnapshot): string[] => {
    const result = [...root.articleIds]
    for (const child of ordered(children.get(root.nodeId) ?? [])) {
      result.push(...descendantArticleIds(child))
    }
    return [...new Set(result)]
  }
  const articlePath = (articleId: string): string => {
    let node = nodes.find((candidate) => candidate.articleIds.includes(articleId)) ?? null
    const titles: string[] = []
    while (node) {
      if (node.title.trim()) titles.unshift(node.title.trim())
      node = node.parentNodeId ? (byId.get(node.parentNodeId) ?? null) : null
    }
    return titles.join(' / ')
  }
  return { orderedNodes: ordered(nodes), descendantArticleIds, articlePath }
}

function contentCandidates(task: Pick<TaskDetail, 'contentVersionSnapshot' | 'structureSnapshot'>): Candidate[] {
  const articles = [...task.contentVersionSnapshot.articles].sort((left, right) => left.order - right.order)
  const byArticle = new Map(articles.map((article) => [article.articleId, article]))
  const tools = structureTools(task.structureSnapshot)
  const candidates: Candidate[] = []

  for (const node of tools.orderedNodes) {
    for (const articleId of tools.descendantArticleIds(node)) {
      const article = byArticle.get(articleId)
      if (!article) continue
      candidates.push({
        targetKind: 'article', articleId, articleNumber: article.number,
        articleOrder: article.order, structurePath: tools.articlePath(articleId),
        fieldKey: 'structureTitle', fieldLabel: '结构标题', text: node.title,
        dedupeKey: `article:${articleId}|structureTitle`,
      })
    }
  }
  for (const article of articles) {
    const common = {
      targetKind: 'article' as const,
      articleId: article.articleId,
      articleNumber: article.number,
      articleOrder: article.order,
      structurePath: tools.articlePath(article.articleId),
    }
    candidates.push({ ...common, fieldKey: 'articleNumber', fieldLabel: '法条编号', text: article.number })
    candidates.push({ ...common, fieldKey: 'articleBody', fieldLabel: '法条正文', text: article.body })
  }
  return candidates
}

function annotationCandidates(
  task: Pick<TaskDetail, 'contentVersionSnapshot' | 'structureSnapshot'>,
  draft: Pick<TaskDraftResponse, 'overallDraft' | 'articleDrafts'>,
): Candidate[] {
  const candidates: Candidate[] = []
  if (draft.overallDraft) {
    for (const fieldKey of Object.keys(OVERALL_FIELD_LABELS) as (keyof typeof OVERALL_FIELD_LABELS)[]) {
      const value = draft.overallDraft[fieldKey]
      if (!value) continue
      candidates.push({
        targetKind: 'overall', articleId: null, articleNumber: null,
        articleOrder: -1, structurePath: '整体信息', fieldKey,
        fieldLabel: OVERALL_FIELD_LABELS[fieldKey], text: value,
      })
    }
  }

  const tools = structureTools(task.structureSnapshot)
  for (const article of [...task.contentVersionSnapshot.articles].sort((left, right) => left.order - right.order)) {
    const values = draft.articleDrafts[article.articleId]
    if (!values) continue
    for (const fieldKey of Object.keys(ARTICLE_FIELD_LABELS) as (keyof typeof ARTICLE_FIELD_LABELS)[]) {
      const rawValue = values[fieldKey]
      if (!rawValue) continue
      const text = fieldKey === 'itemType' && values.itemType
        ? ITEM_TYPE_LABELS[values.itemType]
        : rawValue
      candidates.push({
        targetKind: 'article', articleId: article.articleId, articleNumber: article.number,
        articleOrder: article.order, structurePath: tools.articlePath(article.articleId),
        fieldKey, fieldLabel: ARTICLE_FIELD_LABELS[fieldKey], text,
      })
    }
  }
  return candidates
}

function fieldPriority(fieldKey: string): number {
  const index = FIELD_PRIORITY.indexOf(fieldKey)
  return index < 0 ? FIELD_PRIORITY.length : index
}

export function searchTask(
  task: Pick<TaskDetail, 'lawBaseInfoSnapshot' | 'contentVersionSnapshot' | 'structureSnapshot'>,
  draft: Pick<TaskDraftResponse, 'overallDraft' | 'articleDrafts'>,
  options: SearchOptions,
): AnnotationSearchPage {
  const query = options.query.trim()
  const page = Math.max(1, options.page ?? 1)
  const size = Math.max(1, options.size ?? 10)
  const empty = (error: string | null, active: boolean): AnnotationSearchPage => ({
    active, query, error, items: [], page, size, totalElements: 0, totalPages: 0,
  })
  if (!query) return empty(null, false)
  if ([...query].length > 100) return empty('搜索关键词须为1至100个字符', true)
  if (CONTROL_PATTERN.test(query)) return empty('搜索关键词不得包含控制字符或换行', true)

  const candidates = [
    ...(options.scope === 'ANNOTATION' ? [] : contentCandidates(task)),
    ...(options.scope === 'CONTENT' ? [] : annotationCandidates(task, draft)),
  ]
  const deduped = new Map<string, AnnotationSearchResult>()
  for (const candidate of candidates) {
    for (const matchStart of literalMatches(candidate.text, query)) {
      const target = candidate.targetKind === 'overall'
        ? { kind: 'overall' as const }
        : { kind: 'article' as const, articleId: candidate.articleId! }
      const key = candidate.dedupeKey
        ?? `${candidate.targetKind === 'overall' ? 'overall' : `article:${candidate.articleId}`}|${candidate.fieldKey}|${matchStart}`
      if (deduped.has(key)) continue
      const snippet = snippetFor(candidate.text, matchStart, query.length)
      deduped.set(key, {
        key, lawName: task.lawBaseInfoSnapshot.name, target,
        articleId: candidate.articleId, articleNumber: candidate.articleNumber,
        articleOrder: candidate.articleOrder, structurePath: candidate.structurePath,
        fieldKey: candidate.fieldKey, fieldLabel: candidate.fieldLabel,
        displayText: candidate.text, snippet, segments: buildHighlightSegments(snippet, query),
        matchStart,
      })
    }
  }

  const all = [...deduped.values()].sort((left, right) => {
    const targetOrder = Number(left.target.kind === 'article') - Number(right.target.kind === 'article')
    if (targetOrder) return targetOrder
    if (left.articleOrder !== right.articleOrder) return left.articleOrder - right.articleOrder
    const priority = fieldPriority(left.fieldKey) - fieldPriority(right.fieldKey)
    if (priority) return priority
    if (left.matchStart !== right.matchStart) return left.matchStart - right.matchStart
    return left.key.localeCompare(right.key)
  })
  const totalPages = Math.ceil(all.length / size)
  const offset = (page - 1) * size
  return {
    active: true, query, error: null, items: all.slice(offset, offset + size),
    page, size, totalElements: all.length, totalPages,
  }
}
