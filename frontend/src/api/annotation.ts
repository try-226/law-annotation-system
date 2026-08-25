import type {
  SaveArticleDraftPayload,
  SaveOverallDraftPayload,
  SubmitReviewResult,
  TaskDraftResponse,
} from '../types/annotation'
import { csrfRequest } from './csrf'
import request from './request'
import type { ApiResponse } from './types'

export async function getTaskDraft(taskId: string): Promise<TaskDraftResponse> {
  const { data } = await request.get<ApiResponse<TaskDraftResponse>>(`/tasks/${taskId}/draft`)
  return data.data
}

export async function saveOverallDraft(
  taskId: string,
  payload: SaveOverallDraftPayload,
): Promise<TaskDraftResponse> {
  const { data } = await csrfRequest<ApiResponse<TaskDraftResponse>>({
    method: 'PUT', url: `/tasks/${taskId}/draft/overall`, data: payload,
  })
  return data.data
}

export async function clearOverallDraft(taskId: string): Promise<TaskDraftResponse> {
  const { data } = await csrfRequest<ApiResponse<TaskDraftResponse>>({
    method: 'DELETE', url: `/tasks/${taskId}/draft/overall`,
  })
  return data.data
}

export async function saveArticleDraft(
  taskId: string,
  articleId: string,
  payload: SaveArticleDraftPayload,
): Promise<TaskDraftResponse> {
  const { data } = await csrfRequest<ApiResponse<TaskDraftResponse>>({
    method: 'PUT', url: `/tasks/${taskId}/draft/articles/${articleId}`, data: payload,
  })
  return data.data
}

export async function clearArticleDraft(
  taskId: string,
  articleId: string,
): Promise<TaskDraftResponse> {
  const { data } = await csrfRequest<ApiResponse<TaskDraftResponse>>({
    method: 'DELETE', url: `/tasks/${taskId}/draft/articles/${articleId}`,
  })
  return data.data
}

export async function submitTaskForReview(taskId: string): Promise<SubmitReviewResult> {
  const { data } = await csrfRequest<ApiResponse<SubmitReviewResult>>({
    method: 'POST', url: `/tasks/${taskId}/submit-review`,
  })
  return data.data
}
