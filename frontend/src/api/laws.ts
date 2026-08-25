import { csrfRequest } from './csrf'
import request from './request'
import type { ApiResponse, PageResponse } from './types'
import type {
  LawBaseInfo,
  LawDetail,
  LawImportArticle,
  LawImportConfirmPayload,
  LawImportPreview,
  LawListItem,
  LawStructureInput,
  RecycleLawListItem,
} from '../types/law'

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
  const response = await csrfRequest<ApiResponse<LawImportPreview>>({
    method: 'POST',
    url: '/laws/import/parse',
    data: { fullTextPaste },
  })
  return response.data.data
}

export async function confirmLaw(payload: LawImportConfirmPayload) {
  const response = await csrfRequest<ApiResponse<LawDetail>>({
    method: 'POST',
    url: '/laws/import/confirm',
    data: payload,
  })
  return response.data.data
}

export async function updateLawBase(lawId: string, baseInfo: LawBaseInfo) {
  const response = await csrfRequest<ApiResponse<LawDetail>>({
    method: 'PATCH',
    url: `/laws/${lawId}/base`,
    data: baseInfo,
  })
  return response.data.data
}

export async function updateLawStructure(lawId: string, structure: LawStructureInput[]) {
  const response = await csrfRequest<ApiResponse<LawDetail>>({
    method: 'PATCH',
    url: `/laws/${lawId}/structure`,
    data: { structure },
  })
  return response.data.data
}

export async function addLawArticle(
  lawId: string,
  article: Pick<LawImportArticle, 'number' | 'body' | 'order'>,
) {
  const response = await csrfRequest<ApiResponse<LawDetail>>({
    method: 'POST',
    url: `/laws/${lawId}/articles`,
    data: article,
  })
  return response.data.data
}

export async function updateLawArticle(
  lawId: string,
  articleId: string,
  article: Pick<LawImportArticle, 'number' | 'body' | 'order'>,
) {
  const response = await csrfRequest<ApiResponse<LawDetail>>({
    method: 'PATCH',
    url: `/laws/${lawId}/articles/${articleId}`,
    data: article,
  })
  return response.data.data
}

export async function deleteLawArticle(lawId: string, articleId: string) {
  const response = await csrfRequest<ApiResponse<LawDetail>>({
    method: 'DELETE',
    url: `/laws/${lawId}/articles/${articleId}`,
  })
  return response.data.data
}

export async function deleteLaw(lawId: string) {
  await csrfRequest<ApiResponse<void>>({ method: 'DELETE', url: `/laws/${lawId}` })
}

export async function restoreLaw(lawId: string) {
  const response = await csrfRequest<ApiResponse<LawDetail>>({
    method: 'POST',
    url: `/laws/${lawId}/restore`,
  })
  return response.data.data
}
