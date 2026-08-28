import request from './request'
import type { ApiResponse, PageResponse } from './types'
import type { AdminSearchQuery, SearchHit } from '../types/search'

export async function searchLaws(query: AdminSearchQuery): Promise<PageResponse<SearchHit>> {
  const response = await request.get<ApiResponse<PageResponse<SearchHit>>>('/laws/search', {
    params: {
      q: query.q,
      scope: query.scope,
      page: query.page ?? 0,
      size: query.size ?? 10,
    },
  })
  return response.data.data
}
