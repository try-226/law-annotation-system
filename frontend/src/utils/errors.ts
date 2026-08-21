import { apiErrorResponse } from '../api/request'
import type { ErrorLocator } from '../api/types'

export interface ParsedFailure {
  status?: number
  code?: string
  userMessage?: string
  locators: ErrorLocator[]
  network: boolean
}

export function parseFailure(error: unknown): ParsedFailure {
  const axiosError = apiErrorResponse(error)
  if (!axiosError) {
    return { locators: [], network: false }
  }
  const apiError = axiosError.response?.data?.error
  return {
    status: axiosError.response?.status,
    code: apiError?.code,
    userMessage: apiError?.userMessage,
    locators: Array.isArray(apiError?.locators) ? apiError.locators : [],
    network: !axiosError.response,
  }
}

export function fieldErrors(error: unknown): Record<string, string> {
  return Object.fromEntries(parseFailure(error).locators.map((item) => [item.path, item.message]))
}

export function safeErrorMessage(error: unknown, fallback = '操作失败，请稍后重试'): string {
  const failure = parseFailure(error)
  if (failure.network) {
    return '网络连接异常，请检查网络后重试'
  }
  if (failure.status === 400) {
    return failure.locators[0]?.message ?? '提交内容有误，请检查后重试'
  }
  if (failure.status === 401) {
    return failure.code === 'AUTH.INVALID_CREDENTIALS' ? '账号或密码错误' : '登录已失效，请重新登录'
  }
  if (failure.status === 403) {
    return failure.code === 'AUTH.CSRF_INVALID' ? '请求安全校验失败，请重试' : '无权执行此操作'
  }
  if (failure.status === 404) {
    return '目标用户已不存在，列表将自动刷新'
  }
  if (failure.status === 409 || failure.status === 422) {
    return failure.userMessage || fallback
  }
  if (failure.status && failure.status >= 500) {
    return '服务暂时不可用，请稍后重试'
  }
  return fallback
}
