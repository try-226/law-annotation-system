export type ValidityStatus = 'ACTIVE' | 'NOT_EFFECTIVE' | 'INVALID' | 'REPEALED'
export type StructureNodeType = 'PART' | 'CHAPTER' | 'SECTION'
export type LawDisplayStatus =
  | 'UNANNOTATED'
  | 'ANNOTATING'
  | 'PENDING_REVIEW'
  | 'PARTIALLY_REJECTED'
  | 'PENDING_REREVIEW'
  | 'COMPLETED'
  | 'PENDING_REVISION'
  | 'REVISING'

export interface LawBaseInfo {
  name: string
  issuingAuthority: string
  publicationDate: string
  validityStatus: ValidityStatus
}

export interface LawImportPreviewBaseInfo {
  name: string | null
  issuingAuthority: string | null
  publicationDate: string | null
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
  baseInfo: LawImportPreviewBaseInfo
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
  displayStatus: LawDisplayStatus
  articleCount: number
  updatedAt: string
}

export type RecycleLawListItem = Omit<LawListItem, 'displayStatus'> & {
  pendingRevision: boolean
  deletedAt: string
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
  displayStatus: LawDisplayStatus
  createdAt: string
  updatedAt: string
}

export interface LawImportConfirmPayload {
  baseInfo: LawBaseInfo
  structure: LawStructureInput[]
  articles: LawImportArticle[]
}
