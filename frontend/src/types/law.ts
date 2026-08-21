export type ValidityStatus = 'ACTIVE' | 'NOT_EFFECTIVE' | 'INVALID' | 'REPEALED'
export type StructureNodeType = 'PART' | 'CHAPTER' | 'SECTION'

export interface ApiError {
  code: string
  userMessage: string
  locators: Array<{ field?: string; message?: string }>
}

export interface ApiResponse<T> {
  success: boolean
  data: T
  error: ApiError | null
  timestamp: string
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface LawBaseInfo {
  name: string
  issuingAuthority: string
  publicationDate: string
  validityStatus: ValidityStatus | null
}

export interface LawStructureInput {
  nodeId: string
  type: StructureNodeType
  title: string
  parentNodeId: string | null
  order: number
  articleRefs: string[]
}

export interface LawImportArticle {
  clientKey: string
  number: string
  body: string
  order: number
}

export interface LawValidationIssue {
  code: string
  field: string | null
  articleIndex: number | null
  articleNumber: string | null
  structurePath: string | null
  message: string
}

export interface LawImportPreview {
  baseInfo: LawBaseInfo
  structure: LawStructureInput[]
  articles: LawImportArticle[]
  warnings: string[]
  validationIssues: LawValidationIssue[]
}

export interface LawListItem {
  id: string
  name: string
  issuingAuthority: string
  publicationDate: string
  validityStatus: ValidityStatus
  articleCount: number
  updatedAt: string
}

export interface LawArticle {
  articleId: string
  number: string
  body: string
  order: number
}

export interface LawStructureNode {
  nodeId: string
  type: StructureNodeType
  title: string
  parentNodeId: string | null
  order: number
  articleIds: string[]
}

export interface LawDetail {
  id: string
  name: string
  issuingAuthority: string
  publicationDate: string
  validityStatus: ValidityStatus
  structure: LawStructureNode[]
  articles: LawArticle[]
  currentContentVersionId: string
  currentContentVersionSeq: number
  pendingRevision: boolean
  createdAt: string
  updatedAt: string
}
