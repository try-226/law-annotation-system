import type { SearchHit, SearchScope } from '../../types/search'

export interface HighlightSegment {
  text: string
  highlighted: boolean
}

export const SEARCH_SCOPE_OPTIONS: Array<{ value: SearchScope; label: string }> = [
  { value: 'ALL', label: '全部' },
  { value: 'LAW_TEXT', label: '法律正文' },
  { value: 'ANNOTATION', label: '标注结果' },
]

const FIELD_LABELS: Record<string, string> = {
  'law.name': '法律名称',
  'law.issuingAuthority': '发布机关',
  'structure.title': '章节标题',
  'article.number': '条号',
  'article.body': '法条正文',
  'overallAnnotation.lawCategory': '法律类别',
  'overallAnnotation.overallKeywords': '整体关键词',
  'overallAnnotation.summary': '摘要',
  'overallAnnotation.overallNote': '整体备注',
  'articleAnnotation.itemType': '条目类型',
  'articleAnnotation.keywords': '法条关键词',
  'articleAnnotation.subjects': '涉及主体',
  'articleAnnotation.legalLiability': '法律责任',
  'articleAnnotation.annotationNote': '标注备注',
}

export function searchFieldLabel(field: string): string {
  return FIELD_LABELS[field] ?? field
}

export function splitSearchSnippet(hit: Pick<SearchHit, 'snippet' | 'highlightStart' | 'highlightEnd'>): HighlightSegment[] {
  const start = Math.max(0, Math.min(hit.snippet.length, hit.highlightStart))
  const end = Math.max(start, Math.min(hit.snippet.length, hit.highlightEnd))
  if (start === end) return [{ text: hit.snippet, highlighted: false }]
  return [
    { text: hit.snippet.slice(0, start), highlighted: false },
    { text: hit.snippet.slice(start, end), highlighted: true },
    { text: hit.snippet.slice(end), highlighted: false },
  ].filter((segment) => segment.text.length > 0)
}

export function searchHitKey(hit: SearchHit, index: number): string {
  return [hit.lawId, hit.articleId ?? 'law', hit.hitField, index].join('|')
}

export function searchResultRoute(hit: SearchHit, result = false) {
  const query: Record<string, string> = {}
  if (hit.articleId) query.articleId = hit.articleId
  if (result) query.section = 'formal'
  return {
    name: 'law-detail',
    params: { lawId: hit.lawId },
    ...(Object.keys(query).length ? { query } : {}),
  }
}

export function isAnnotationHit(hit: SearchHit): boolean {
  return hit.hitSource === 'OVERALL_ANNOTATION' || hit.hitSource === 'ARTICLE_ANNOTATION'
}
