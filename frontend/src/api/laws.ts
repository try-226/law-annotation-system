import type { AxiosRequestConfig } from 'axios'

import request from './request'
import type {
  ApiResponse,
  LawBaseInfo,
  LawDetail,
  LawDisplayStatus,
  LawImportArticle,
  LawImportPreview,
  LawListItem,
  LawStructureInput,
  PageResponse,
  RecycleLawListItem,
  ValidityStatus,
} from '../types/law'
import { safeErrorMessage } from '../utils/errors'

interface CsrfToken {
  headerName: string
  token: string
}

async function csrfConfig(): Promise<AxiosRequestConfig> {
  const response = await request.get<ApiResponse<CsrfToken>>('/auth/csrf')
  return { headers: { [response.data.data.headerName]: response.data.data.token } }
}

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
  const response = await request.post<ApiResponse<LawImportPreview>>(
    '/laws/import/parse',
    { fullTextPaste },
    await csrfConfig(),
  )
  return response.data.data
}

export async function confirmLaw(preview: LawImportPreview) {
  const response = await request.post<ApiResponse<LawDetail>>(
    '/laws/import/confirm',
    {
      baseInfo: preview.baseInfo,
      structure: preview.structure,
      articles: preview.articles,
    },
    await csrfConfig(),
  )
  return response.data.data
}

export async function updateLawBase(lawId: string, baseInfo: LawBaseInfo) {
  const response = await request.patch<ApiResponse<LawDetail>>(
    `/laws/${lawId}/base`,
    baseInfo,
    await csrfConfig(),
  )
  return response.data.data
}

export async function updateLawStructure(lawId: string, structure: LawStructureInput[]) {
  const response = await request.patch<ApiResponse<LawDetail>>(
    `/laws/${lawId}/structure`,
    { structure },
    await csrfConfig(),
  )
  return response.data.data
}

export async function addLawArticle(
  lawId: string,
  article: Pick<LawImportArticle, 'number' | 'body' | 'order'>,
) {
  const response = await request.post<ApiResponse<LawDetail>>(
    `/laws/${lawId}/articles`,
    article,
    await csrfConfig(),
  )
  return response.data.data
}

export async function updateLawArticle(
  lawId: string,
  articleId: string,
  article: Pick<LawImportArticle, 'number' | 'body' | 'order'>,
) {
  const response = await request.patch<ApiResponse<LawDetail>>(
    `/laws/${lawId}/articles/${articleId}`,
    article,
    await csrfConfig(),
  )
  return response.data.data
}

export async function deleteLawArticle(lawId: string, articleId: string) {
  const response = await request.delete<ApiResponse<LawDetail>>(
    `/laws/${lawId}/articles/${articleId}`,
    await csrfConfig(),
  )
  return response.data.data
}

export async function deleteLaw(lawId: string) {
  await request.delete(`/laws/${lawId}`, await csrfConfig())
}

export async function restoreLaw(lawId: string) {
  const response = await request.post<ApiResponse<LawDetail>>(
    `/laws/${lawId}/restore`,
    undefined,
    await csrfConfig(),
  )
  return response.data.data
}

export function apiErrorMessage(error: unknown, fallback = '请求失败，请检查输入后重试'): string {
  return safeErrorMessage(error, fallback)
}
