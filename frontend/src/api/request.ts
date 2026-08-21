import axios, { type AxiosError } from 'axios'

import type { ApiResponse } from './types'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10_000,
  withCredentials: true,
})

request.interceptors.request.use(
  (config) => config,
  (error: unknown) => Promise.reject(error),
)

request.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const url = error.config?.url ?? ''
      const isSessionRestore = error.config?.method?.toUpperCase() === 'GET' && url.endsWith('/auth/me')
      if (!url.endsWith('/auth/login') && !isSessionRestore) {
        unauthorizedHandler?.()
      }
    }
    return Promise.reject(error)
  },
)

let unauthorizedHandler: (() => void) | undefined

export function setUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler
}

export function apiErrorResponse(error: unknown): AxiosError<ApiResponse<never>> | null {
  return axios.isAxiosError<ApiResponse<never>>(error) ? error : null
}

export default request
