import { csrfRequest } from './csrf'
import request from './request'
import type { ApiResponse, PageResponse, Role, User } from './types'

export interface UserQuery {
  search?: string
  role?: Role
  enabled?: boolean
  page: number
  size: number
}

export interface CreateUserPayload {
  name: string
  loginAccount: string
  initialPassword: string
  role: Role
}

export interface ResetPasswordPayload {
  newPassword: string
  confirmPassword: string
}

export async function listUsers(query: UserQuery): Promise<PageResponse<User>> {
  const { data } = await request.get<ApiResponse<PageResponse<User>>>('/users', { params: query })
  return data.data
}

export async function createUser(payload: CreateUserPayload): Promise<User> {
  const { data } = await csrfRequest<ApiResponse<User>>({
    method: 'POST',
    url: '/users',
    data: payload,
  })
  return data.data
}

export async function updateUserName(id: string, name: string): Promise<User> {
  const { data } = await csrfRequest<ApiResponse<User>>({
    method: 'PATCH',
    url: `/users/${id}`,
    data: { name },
  })
  return data.data
}

export async function resetUserPassword(id: string, payload: ResetPasswordPayload): Promise<void> {
  await csrfRequest<ApiResponse<null>>({
    method: 'POST',
    url: `/users/${id}/reset-password`,
    data: payload,
  })
}

export async function setUserEnabled(id: string, enabled: boolean): Promise<User> {
  const { data } = await csrfRequest<ApiResponse<User>>({
    method: 'POST',
    url: `/users/${id}/${enabled ? 'enable' : 'disable'}`,
  })
  return data.data
}

export async function deleteUser(id: string): Promise<void> {
  await csrfRequest<ApiResponse<null>>({ method: 'DELETE', url: `/users/${id}` })
}
