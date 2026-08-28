export type ValidityStatus = 'ACTIVE' | 'NOT_EFFECTIVE' | 'INVALID' | 'REPEALED'
export type StructureNodeType = 'PART' | 'CHAPTER' | 'SECTION'

export const VALIDITY_STATUS_LABELS: Record<ValidityStatus, string> = {
  ACTIVE: '现行有效',
  NOT_EFFECTIVE: '尚未生效',
  INVALID: '失效',
  REPEALED: '已废止',
}

export type LawDisplayStatus =
  | 'UNANNOTATED'
  | 'ANNOTATING'
  | 'PENDING_REVIEW'
  | 'PARTIALLY_REJECTED'
  | 'PENDING_REREVIEW'
  | 'PENDING_REVISION'
  | 'REVISING'
  | 'COMPLETED'

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
  displayStatus: LawDisplayStatus
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
  displayStatus: LawDisplayStatus
  structure: LawStructureNode[]
  articles: LawArticle[]
  currentContentVersionId: string
  currentContentVersionSeq: number
  currentAnnotationVersionId: string | null
  pendingRevision: boolean
  createdAt: string
  updatedAt: string
}

export interface RecycleLawListItem {
  id: string
  name: string
  issuingAuthority: string
  publicationDate: string
  validityStatus: ValidityStatus
  articleCount: number
  pendingRevision: boolean
  deletedAt: string
  updatedAt: string
}
