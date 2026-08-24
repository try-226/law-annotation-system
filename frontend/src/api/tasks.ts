import { csrfRequest } from './csrf'
import request from './request'
import type { ApiResponse, PageResponse } from './types'
import type {
  CancelTaskPayload,
  CreateOrdinaryTaskPayload,
  TaskDetail,
  TaskListItem,
  TaskQuery,
} from '../types/task'

export async function listTasks(query: TaskQuery): Promise<PageResponse<TaskListItem>> {
  const { data } = await request.get<ApiResponse<PageResponse<TaskListItem>>>('/tasks', {
    params: query,
  })
  return data.data
}

export async function getTask(taskId: string): Promise<TaskDetail> {
  const { data } = await request.get<ApiResponse<TaskDetail>>(`/tasks/${taskId}`)
  return data.data
}

export async function createOrdinaryTask(
  payload: CreateOrdinaryTaskPayload,
): Promise<TaskDetail> {
  const { data } = await csrfRequest<ApiResponse<TaskDetail>>({
    method: 'POST',
    url: '/tasks/ordinary',
    data: payload,
  })
  return data.data
}

export async function startTask(taskId: string): Promise<TaskDetail> {
  const { data } = await csrfRequest<ApiResponse<TaskDetail>>({
    method: 'POST',
    url: `/tasks/${taskId}/start`,
  })
  return data.data
}

export async function cancelTask(
  taskId: string,
  payload: CancelTaskPayload,
): Promise<TaskDetail> {
  const { data } = await csrfRequest<ApiResponse<TaskDetail>>({
    method: 'POST',
    url: `/tasks/${taskId}/cancel`,
    data: payload,
  })
  return data.data
}
