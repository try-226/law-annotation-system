export type SearchScope = 'ALL' | 'LAW_TEXT' | 'ANNOTATION'

export type SearchHitSource =
  | 'LAW_NAME'
  | 'ISSUING_AUTHORITY'
  | 'STRUCTURE_TITLE'
  | 'ARTICLE_NUMBER'
  | 'ARTICLE_BODY'
  | 'OVERALL_ANNOTATION'
  | 'ARTICLE_ANNOTATION'

export interface SearchHit {
  lawId: string
  lawName: string
  articleId: string | null
  articleNumber: string | null
  structurePath: string[]
  hitSource: SearchHitSource
  hitField: string
  snippet: string
  highlightStart: number
  highlightEnd: number
}

export interface AdminSearchQuery {
  q: string
  scope: SearchScope
  page?: number
  size?: number
}
