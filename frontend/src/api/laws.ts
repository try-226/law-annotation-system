import { csrfRequest } from './csrf'
import request from './request'
import type { ApiResponse, PageResponse } from './types'
import type {
  LawBaseInfo,
  LawDetail,
  LawDisplayStatus,
  LawImportArticle,
  LawImportPreview,
  LawListItem,
  LawStructureInput,
  RecycleLawListItem,
  ValidityStatus,
} from '../types/law'
import { safeErrorMessage } from '../utils/errors'

export interface LawListQuery {
  name?: string
  validityStatus?: ValidityStatus
  displayStatus?: LawDisplayStatus
  page?: number
  size?: number
}

export function listLaws(name: string, page?: number): Promise<PageResponse<LawListItem>>
export function listLaws(query: LawListQuery): Promise<PageResponse<LawListItem>>
export async function listLaws(
  nameOrQuery: string | LawListQuery,
  legacyPage = 0,
): Promise<PageResponse<LawListItem>> {
  const query: LawListQuery = typeof nameOrQuery === 'string'
    ? { name: nameOrQuery, page: legacyPage, size: 10 }
    : nameOrQuery
  const response = await request.get<ApiResponse<PageResponse<LawListItem>>>('/laws', {
    params: {
      name: query.name?.trim() || undefined,
      validityStatus: query.validityStatus || undefined,
      displayStatus: query.displayStatus || undefined,
      page: query.page ?? 0,
      size: query.size ?? 10,
    },
  })
  return response.data.data
}

export async function listRecycleLaws(name: string, page = 0, size = 10) {
  const response = await request.get<ApiResponse<PageResponse<RecycleLawListItem>>>('/laws/recycle', {
    params: { name: name.trim() || undefined, page, size },
  })
  return response.data.data
}

export async function getLaw(lawId: string) {
  const response = await request.get<ApiResponse<LawDetail>>(`/laws/${lawId}`)
  return response.data.data
}

export async function parseLaw(fullTextPaste: string) {
  const { data } = await csrfRequest<ApiResponse<LawImportPreview>>({
    method: 'POST',
    url: '/laws/import/parse',
    data: { fullTextPaste },
  })
  return data.data
}

export async function confirmLaw(preview: LawImportPreview) {
  const { data } = await csrfRequest<ApiResponse<LawDetail>>({
    method: 'POST',
    url: '/laws/import/confirm',
    data: {
      baseInfo: preview.baseInfo,
      structure: preview.structure,
      articles: preview.articles,
    },
  })
  return data.data
}

export async function updateLawBase(lawId: string, baseInfo: LawBaseInfo) {
  const { data } = await csrfRequest<ApiResponse<LawDetail>>({
    method: 'PATCH', url: `/laws/${lawId}/base`, data: baseInfo,
  })
  return data.data
}

export async function updateLawStructure(lawId: string, structure: LawStructureInput[]) {
  const { data } = await csrfRequest<ApiResponse<LawDetail>>({
    method: 'PATCH', url: `/laws/${lawId}/structure`, data: { structure },
  })
  return data.data
}

export async function addLawArticle(
  lawId: string,
  article: Pick<LawImportArticle, 'number' | 'body' | 'order'>,
) {
  const { data } = await csrfRequest<ApiResponse<LawDetail>>({
    method: 'POST', url: `/laws/${lawId}/articles`, data: article,
  })
  return data.data
}

export async function updateLawArticle(
  lawId: string,
  articleId: string,
  article: Pick<LawImportArticle, 'number' | 'body' | 'order'>,
) {
  const { data } = await csrfRequest<ApiResponse<LawDetail>>({
    method: 'PATCH', url: `/laws/${lawId}/articles/${articleId}`, data: article,
  })
  return data.data
}

export async function deleteLawArticle(lawId: string, articleId: string) {
  const { data } = await csrfRequest<ApiResponse<LawDetail>>({
    method: 'DELETE', url: `/laws/${lawId}/articles/${articleId}`,
  })
  return data.data
}

export async function deleteLaw(lawId: string) {
  await csrfRequest<ApiResponse<null>>({ method: 'DELETE', url: `/laws/${lawId}` })
}

export async function restoreLaw(lawId: string) {
  const { data } = await csrfRequest<ApiResponse<LawDetail>>({
    method: 'POST', url: `/laws/${lawId}/restore`,
  })
  return data.data
}

export function apiErrorMessage(error: unknown, fallback = '请求失败，请检查输入后重试'): string {
  return safeErrorMessage(error, fallback)
}
