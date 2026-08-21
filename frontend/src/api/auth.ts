import { csrfRequest } from './csrf'
import request from './request'
import type { ApiResponse, CurrentUser, Role } from './types'

export interface LoginPayload {
  loginAccount: string
  password: string
  expectedRole: Role
}

export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

export async function fetchCurrentUser(): Promise<CurrentUser> {
  const { data } = await request.get<ApiResponse<CurrentUser>>('/auth/me')
  return data.data
}

export async function login(payload: LoginPayload): Promise<CurrentUser> {
  const { data } = await csrfRequest<ApiResponse<CurrentUser>>({
    method: 'POST',
    url: '/auth/login',
    data: payload,
  })
  return data.data
}

export async function logout(): Promise<void> {
  await csrfRequest<ApiResponse<null>>({ method: 'POST', url: '/auth/logout' })
}

export async function updateProfile(name: string): Promise<CurrentUser> {
  const { data } = await csrfRequest<ApiResponse<CurrentUser>>({
    method: 'PATCH',
    url: '/auth/me',
    data: { name },
  })
  return data.data
}

export async function changePassword(payload: ChangePasswordPayload): Promise<void> {
  await csrfRequest<ApiResponse<null>>({
    method: 'POST',
    url: '/auth/me/password',
    data: payload,
  })
}
