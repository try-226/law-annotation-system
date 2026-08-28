import axios, { type AxiosResponse } from 'axios'

import { clearCsrfToken, csrfRequest } from './csrf'
import type { ApiResponse } from './types'
import type { ExportDownload, LawExportRequest } from '../types/export'
import { filenameFromContentDisposition } from '../views/export/exportDownload'

async function normalizeBlobApiError(error: unknown): Promise<void> {
  if (!axios.isAxiosError<Blob>(error) || !error.response || !(error.response.data instanceof Blob)) {
    return
  }
  try {
    const parsed: unknown = JSON.parse(await error.response.data.text())
    ;(error.response as AxiosResponse<unknown>).data = parsed
  } catch {
    // Keep the original Axios error when the response is not a JSON API envelope.
  }
}

function apiErrorCode(error: unknown): string | undefined {
  if (!axios.isAxiosError<ApiResponse<never>>(error)) return undefined
  return error.response?.data?.error?.code
}

export async function exportLaw(lawId: string, payload: LawExportRequest): Promise<ExportDownload> {
  for (let attempt = 0; attempt < 2; attempt += 1) {
    try {
      const response = await csrfRequest<Blob>({
        method: 'POST',
        url: `/laws/${lawId}/export`,
        data: payload,
        responseType: 'blob',
      })
      const fallback = `law-${lawId}-${payload.type.toLowerCase()}.${payload.format.toLowerCase()}`
      return {
        blob: response.data,
        filename: filenameFromContentDisposition(response.headers['content-disposition'], fallback),
      }
    } catch (error: unknown) {
      await normalizeBlobApiError(error)
      if (attempt === 0 && apiErrorCode(error) === 'AUTH.CSRF_INVALID') {
        clearCsrfToken()
        continue
      }
      throw error
    }
  }
  throw new Error('导出请求未执行')
}
