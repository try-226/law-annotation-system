export type Role = 'ADMIN' | 'ANNOTATOR'

export interface ErrorLocator {
  path: string
  message: string
}

export interface ApiError {
  code: string
  userMessage: string
  locators: ErrorLocator[]
}

export interface ApiResponse<T> {
  success: boolean
  data: T
  error: ApiError | null
  timestamp: string
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface CurrentUser {
  id: string
  name: string
  loginAccount: string
  role: Role
}

export interface User extends CurrentUser {
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface CsrfTokenData {
  headerName: string
  token: string
}
