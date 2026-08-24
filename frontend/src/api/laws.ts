import type { AxiosRequestConfig } from 'axios'

import request from './request'
import type {
  ApiResponse,
  LawBaseInfo,
  LawDetail,
  LawImportArticle,
  LawImportPreview,
  LawListItem,
  LawStructureInput,
  PageResponse,
  RecycleLawListItem,
} from '../types/law'

interface CsrfToken {
  headerName: string
  token: string
}

async function csrfConfig(): Promise<AxiosRequestConfig> {
  const response = await request.get<ApiResponse<CsrfToken>>('/auth/csrf')
  return { headers: { [response.data.data.headerName]: response.data.data.token } }
}

export async function listLaws(name: string, page = 0) {
  const response = await request.get<ApiResponse<PageResponse<LawListItem>>>('/laws', {
    params: { name: name || undefined, page, size: 10 },
  })
  return response.data.data
}

export async function getLaw(lawId: string) {
  const response = await request.get<ApiResponse<LawDetail>>(`/laws/${lawId}`)
  return response.data.data
}

export async function listRecycleLaws(name: string, page = 0) {
  const response = await request.get<ApiResponse<PageResponse<RecycleLawListItem>>>('/laws/recycle', {
    params: { name: name || undefined, page, size: 10 },
  })
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

export function apiErrorMessage(error: unknown): string {
  if (typeof error !== 'object' || error === null || !('response' in error)) {
    return '请求失败，请稍后重试'
  }
  const response = (error as { response?: { data?: ApiResponse<unknown> } }).response
  return response?.data?.error?.userMessage || '请求失败，请检查输入后重试'
}
