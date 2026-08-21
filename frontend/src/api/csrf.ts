import type { AxiosRequestConfig, AxiosResponse } from 'axios'

import request, { apiErrorResponse } from './request'
import type { ApiResponse, CsrfTokenData } from './types'

let cachedToken: CsrfTokenData | null = null
let tokenRequest: Promise<CsrfTokenData> | null = null

export function clearCsrfToken(): void {
  cachedToken = null
  tokenRequest = null
}

async function loadCsrfToken(): Promise<CsrfTokenData> {
  if (cachedToken) {
    return cachedToken
  }
  if (tokenRequest) {
    return tokenRequest
  }

  tokenRequest = request
    .get<ApiResponse<CsrfTokenData>>('/auth/csrf')
    .then(({ data }) => {
      if (!data.success || !data.data?.headerName || !data.data?.token) {
        throw new Error('Invalid CSRF response')
      }
      cachedToken = data.data
      return data.data
    })
    .finally(() => {
      tokenRequest = null
    })

  return tokenRequest
}

async function sendWithToken<T>(config: AxiosRequestConfig): Promise<AxiosResponse<T>> {
  const csrf = await loadCsrfToken()
  return request.request<T>({
    ...config,
    headers: {
      ...(config.headers ?? {}),
      [csrf.headerName]: csrf.token,
    },
  })
}

export async function csrfRequest<T>(config: AxiosRequestConfig): Promise<AxiosResponse<T>> {
  try {
    return await sendWithToken<T>(config)
  } catch (error: unknown) {
    const response = apiErrorResponse(error)
    if (response?.response?.data?.error?.code !== 'AUTH.CSRF_INVALID') {
      throw error
    }
    clearCsrfToken()
    return sendWithToken<T>(config)
  }
}
