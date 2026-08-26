import { csrfRequest } from './csrf'
import request from './request'
import type { ApiResponse } from './types'
import type { ReviewDetail, ReviewTarget } from '../types/review'
import { reviewBasePath, reviewItemPath, reviewRoundPath } from './reviewPath'

export async function getReview(taskId: string): Promise<ReviewDetail> {
  const { data } = await request.get<ApiResponse<ReviewDetail>>(reviewBasePath(taskId))
  return data.data
}

export async function startReview(taskId: string): Promise<ReviewDetail> {
  const { data } = await csrfRequest<ApiResponse<ReviewDetail>>({
    method: 'POST',
    url: `${reviewBasePath(taskId)}/start`,
  })
  return data.data
}

export async function checkReviewItem(
  taskId: string,
  roundId: string,
  target: ReviewTarget,
): Promise<ReviewDetail> {
  const { data } = await csrfRequest<ApiResponse<ReviewDetail>>({
    method: 'POST',
    url: `${reviewItemPath(taskId, roundId, target)}/check`,
  })
  return data.data
}

export async function issueReviewItem(
  taskId: string,
  roundId: string,
  target: ReviewTarget,
  reason: string,
): Promise<ReviewDetail> {
  const { data } = await csrfRequest<ApiResponse<ReviewDetail>>({
    method: 'POST',
    url: `${reviewItemPath(taskId, roundId, target)}/issue`,
    data: { reason },
  })
  return data.data
}

export async function completeReviewRound(taskId: string, roundId: string): Promise<ReviewDetail> {
  const { data } = await csrfRequest<ApiResponse<ReviewDetail>>({
    method: 'POST',
    url: `${reviewRoundPath(taskId, roundId)}/complete`,
  })
  return data.data
}
