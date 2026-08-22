import type { AxiosRequestConfig } from 'axios'

import request from './request'
import type { ApiResponse } from './types'

export type FieldConfigScope = 'OVERALL' | 'ARTICLE'
export type FieldValueKind = 'SELECT' | 'TEXT' | 'TEXTAREA'

export interface FieldConfigItem {
  fieldKey: string
  displayName: string
  type: FieldValueKind
  scope: FieldConfigScope
  required: boolean
  configurable: boolean
}

export interface FieldConfig {
  fields: FieldConfigItem[]
}

interface CsrfToken {
  headerName: string
  token: string
}

async function csrfConfig(): Promise<AxiosRequestConfig> {
  const response = await request.get<ApiResponse<CsrfToken>>('/auth/csrf')
  return { headers: { [response.data.data.headerName]: response.data.data.token } }
}

export async function getFieldConfig(): Promise<FieldConfig> {
  const response = await request.get<ApiResponse<FieldConfig>>('/field-config')
  return response.data.data
}

export async function updateFieldRequired(
  fieldKey: string,
  required: boolean,
): Promise<FieldConfig> {
  const response = await request.patch<ApiResponse<FieldConfig>>(
    '/field-config',
    { fieldKey, required },
    await csrfConfig(),
  )
  return response.data.data
}
